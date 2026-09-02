package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.RelativeCandidate;
import dev.otectus.mcaquests.quest.DisplayNames;
import dev.otectus.mcaquests.quest.situation.SituationFocus;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Selects a concrete MCA villager <em>relative to the quest giver</em> for the NPC objective types
 * (escort, protect, defend, heal, cure, deliver). Resolution is always by UUID through
 * {@link ServerLevel#getEntity(UUID)} / {@link McaCompat}, so an unloaded target simply resolves to
 * {@code empty} (the objective pauses) rather than failing — never trusting the client.
 *
 * <pre>
 * { "mode": "self" }                                     // the quest giver
 * { "mode": "profession", "profession": "minecraft:weaponsmith" }
 * { "mode": "family", "relation": "sibling" }            // any|spouse|parent|child|sibling|grandparent
 * { "mode": "family", "relation": "child", "require": "missing" }
 * { "mode": "situation_focus" }                          // the villager the situation is about
 * { "mode": "uuid", "uuid": "&lt;uuid&gt;" }
 * </pre>
 *
 * <h2>{@code require}: who a family target may name</h2>
 *
 * <p>A {@code family} target used to take whoever came first out of MCA's family tree — dead or alive,
 * real or one of the two ancestors MCA invents for every villager it spawns, present in the world or
 * nowhere at all. A quest could be gated on "is there a sibling in this village?" and then be handed a
 * different sibling entirely, and because MCA never removes the dead from a village's resident roll, that
 * gate counted the departed. Players were sent to deliver letters to brothers who had died.
 *
 * <p>{@code require} names the {@link RelativeCandidate} status the target must satisfy, and it
 * <b>defaults to {@code reachable}</b> — a real, findable person. The safe answer is the default, so a
 * pack that has not thought about it gets the fix for free, and a pack that genuinely wants a dead or
 * missing target must say so ({@code "require": "dead"}, {@code "require": "missing"},
 * {@code "require": "any_known"}). The gate, this selector, {@code matches} and the display name all
 * filter the same candidate list with the same predicate.
 */
public record VillagerTarget(Mode mode, Optional<ResourceLocation> profession,
                             Optional<String> relation, Optional<UUID> uuid,
                             Optional<String> require) {

    public enum Mode {
        SELF,
        PROFESSION,
        FAMILY,
        UUID,
        /**
         * The villager an open situation is <em>about</em> — the one who collapsed, caught the infection,
         * or went missing. Only meaningful on a situation offer; anywhere else it resolves to nothing,
         * which makes the objective unofferable rather than silently pointing somewhere else.
         */
        SITUATION_FOCUS
    }

    /** Relations understood by {@code family} mode (mirrors {@link McaCompat#giverRelativeUuids}). */
    public static final Set<String> RELATIONS =
            Set.of("any", "spouse", "parent", "child", "sibling", "grandparent");

    private static final double FALLBACK_SCAN_RADIUS = 48.0D;

    private static final Codec<Mode> MODE_CODEC = Codec.STRING.flatXmap(
            s -> {
                try {
                    return DataResult.success(Mode.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown villager target mode: " + s);
                }
            },
            m -> DataResult.success(m.name().toLowerCase(Locale.ROOT)));

    public static final MapCodec<VillagerTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MODE_CODEC.fieldOf("mode").forGetter(VillagerTarget::mode),
            ResourceLocation.CODEC.optionalFieldOf("profession").forGetter(VillagerTarget::profession),
            Codec.STRING.optionalFieldOf("relation").forGetter(VillagerTarget::relation),
            UUIDUtil.STRING_CODEC.optionalFieldOf("uuid").forGetter(VillagerTarget::uuid),
            Codec.STRING.optionalFieldOf("require").forGetter(VillagerTarget::require)
    ).apply(instance, VillagerTarget::new));

    public static final Codec<VillagerTarget> CODEC = MAP_CODEC.codec();

    /**
     * The pre-{@code require} shape, so add-ons and older call sites that build a target in code keep
     * compiling. An absent {@code require} means the default for the mode, which is the safe one.
     */
    public VillagerTarget(Mode mode, Optional<ResourceLocation> profession, Optional<String> relation,
                          Optional<UUID> uuid) {
        this(mode, profession, relation, uuid, Optional.empty());
    }

    /** The default target: the quest giver. */
    public static final VillagerTarget SELF =
            new VillagerTarget(Mode.SELF, Optional.empty(), Optional.empty(), Optional.empty());

    /** The relation this target selects, defaulting to immediate family. */
    public String effectiveRelation() {
        return relation.orElse("any");
    }

    /**
     * The status a candidate must satisfy to be this target.
     *
     * <p>{@code family} defaults to {@code reachable}: a real person who is not dead, not one of MCA's
     * invented ancestors, not a player, and either loaded or on a village roll that says where to find
     * them. Every other mode already names exactly one villager, so their default is the loose
     * {@code any_known} and {@code require} is meaningless on them.
     */
    public String effectiveRequire() {
        return require.orElse(mode == Mode.FAMILY ? RelativeCandidate.DEFAULT_FAMILY_REQUIRE : "any_known");
    }

    /** True when this target asserts its villager can actually be found, and so needs a gate. */
    public boolean requiresExistence() {
        return mode == Mode.FAMILY && RelativeCandidate.EXISTENCE_STATUSES.contains(effectiveRequire());
    }

    /** The concrete, currently-loaded villager this target selects, or {@code empty} if unavailable. */
    public Optional<LivingEntity> resolve(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return resolve(player, active, level, null);
    }

    /**
     * As {@link #resolve(ServerPlayer, ActiveQuest, ServerLevel)}, but honours a {@code bound} villager
     * already locked into the objective's {@code ObjectiveProgress}. Once a quest has bound a target this
     * is the only villager it will ever mean — {@code family} mode must not drift to whichever relative
     * happens to be loaded, or the objective line, the glow, and the villager that credits the objective
     * can all name different people.
     */
    public Optional<LivingEntity> resolve(ServerPlayer player, ActiveQuest active, ServerLevel level,
                                          @Nullable UUID bound) {
        if (bound != null) {
            return living(level.getEntity(bound));
        }
        if (mode == Mode.SITUATION_FOCUS) {
            return SituationFocus.focalVillager(level.getServer(), active.situationInstance().orElse(null))
                    .flatMap(u -> living(level.getEntity(u)));
        }
        return resolveFrom(player, level.getEntity(active.villagerUuid()), level, active.questId());
    }

    /**
     * As {@link #resolve(ServerPlayer, ActiveQuest, ServerLevel)}, but keyed on the giver entity rather
     * than on an accepted quest — every mode resolves <em>relative to the giver</em>, and the
     * {@link ActiveQuest} was only ever used to look that giver up.
     *
     * <p>This is what lets an objective answer "would I already be satisfied?" and "can I even be
     * offered?" while a quest is merely being <em>offered</em>, when no {@link ActiveQuest} exists yet.
     */
    public Optional<LivingEntity> resolveFrom(ServerPlayer player, @Nullable Entity giver, ServerLevel level) {
        return resolveFrom(player, giver, level, null);
    }

    /**
     * As {@link #resolveFrom(ServerPlayer, Entity, ServerLevel)}, with the quest id in hand so a
     * {@code situation_focus} target can find the open instance the offer came from. Passing {@code null}
     * simply makes that one mode unresolvable, which is the correct answer outside a situation offer.
     */
    public Optional<LivingEntity> resolveFrom(ServerPlayer player, @Nullable Entity giver, ServerLevel level,
                                              @Nullable ResourceLocation questId) {
        return switch (mode) {
            case SELF -> living(giver);
            case UUID -> uuid.flatMap(u -> living(level.getEntity(u)));
            case FAMILY -> selectRelative(giver, level).flatMap(u -> living(level.getEntity(u)));
            case SITUATION_FOCUS -> giver == null || questId == null ? Optional.empty()
                    : SituationFocus.focalVillager(level.getServer(), giver, questId)
                            .flatMap(u -> living(level.getEntity(u)));
            case PROFESSION -> resolveProfession(player, giver, level);
        };
    }

    /**
     * The relative this target names, or {@code empty} when the giver has none who satisfies
     * {@code require} — which is a first-class answer meaning "this quest must not be offered", not a
     * transient failure.
     */
    public Optional<UUID> selectRelative(@Nullable Entity giver, ServerLevel level) {
        if (mode != Mode.FAMILY || giver == null) {
            return Optional.empty();
        }
        return McaCompat.findGiverRelative(level, giver, effectiveRelation(), effectiveRequire());
    }

    /**
     * The relative to <b>bind</b> this target to, which is a slightly different question from the one
     * the offer gate asked.
     *
     * <p>The gate ran when the villager's offers were drawn. Since 1.4.3 those offers are remembered
     * rather than recomputed, so the player may accept minutes or hours later — and a
     * {@code "require": "nearby"} target's answer is only true while the relative is standing within
     * twelve blocks of the giver. By the time the quest is accepted they have very often wandered off,
     * {@link #selectRelative} finds nobody, nothing is bound, and the objective becomes permanently
     * uncreditable in complete silence.
     *
     * <p>So: prefer exactly the relative the gate would have chosen; failing that, bind the same
     * relation under {@link RelativeCandidate#matchesIdentity} — the same person, minus the distance
     * test that only ever described a moment. This is not a looser <em>credit</em> check; credit is a
     * UUID comparison against whoever is bound here.
     */
    public Optional<UUID> selectRelativeForBinding(@Nullable Entity giver, ServerLevel level) {
        Optional<UUID> exact = selectRelative(giver, level);
        if (exact.isPresent() || mode != Mode.FAMILY || giver == null) {
            return exact;
        }
        return McaCompat.relativeCandidates(level, giver, effectiveRelation()).stream()
                .filter(candidate -> candidate.matchesIdentity(effectiveRequire()))
                .map(RelativeCandidate::uuid)
                .findFirst();
    }

    /** The candidates this target would choose from — the list the offer-time gate also reads. */
    public List<RelativeCandidate> candidates(@Nullable Entity giver, ServerLevel level) {
        if (mode != Mode.FAMILY || giver == null) {
            return List.of();
        }
        return McaCompat.relativeCandidates(level, giver, effectiveRelation()).stream()
                .filter(candidate -> candidate.matches(effectiveRequire()))
                .toList();
    }

    /** True when {@code candidate} is the villager this target refers to (for interaction/event credit). */
    public boolean matches(LivingEntity candidate, ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return matches(candidate, player, active, level, null);
    }

    /**
     * As {@link #matches(LivingEntity, ServerPlayer, ActiveQuest, ServerLevel)}, but exact once a
     * {@code bound} villager has been locked in. Without a binding, {@code family} mode credits <em>any</em>
     * relative of that relation who satisfies {@code require}, so a parcel meant for one sibling could be
     * handed to another; with one, only the villager the quest actually named counts.
     */
    public boolean matches(LivingEntity candidate, ServerPlayer player, ActiveQuest active, ServerLevel level,
                           @Nullable UUID bound) {
        if (!McaCompat.isMcaVillager(candidate)) {
            return false;
        }
        if (bound != null) {
            return candidate.getUUID().equals(bound);
        }
        return switch (mode) {
            case SELF -> candidate.getUUID().equals(active.villagerUuid());
            case UUID -> uuid.map(candidate.getUUID()::equals).orElse(false);
            case PROFESSION -> profession
                    .map(p -> McaCompat.getProfessionId(candidate).map(p::equals).orElse(false))
                    .orElse(false);
            // Filtered by require for the same reason the selector is: a quest that may not be ABOUT a
            // dead relative must not be CREDITED by one either -- but by the IDENTITY half of require,
            // never the positional half. This branch used to re-run the selection query in full, so a
            // "require": "nearby" delivery credited only while the recipient stood within twelve blocks
            // of the QUEST GIVER: the one arrangement the player has just undone by walking over to
            // them. It is reached only when nothing is bound yet; every bound target is the UUID
            // comparison above, which is what the other four modes have always done.
            case FAMILY -> giver(level, active)
                    .map(g -> McaCompat.relativeCandidates(level, g, effectiveRelation()).stream()
                            .filter(relative -> relative.matchesIdentity(effectiveRequire()))
                            .anyMatch(relative -> relative.uuid().equals(candidate.getUUID())))
                    .orElse(false);
            case SITUATION_FOCUS -> SituationFocus
                    .focalVillager(level.getServer(), active.situationInstance().orElse(null))
                    .map(candidate.getUUID()::equals)
                    .orElse(false);
        };
    }

    /**
     * A generic label for this target, used when no concrete villager has resolved yet.
     *
     * <p>{@code family} relations are resolved <em>relative to the quest giver</em>, never to the player,
     * so the {@code mcaquests.target.relation.*} strings must read from the giver's perspective — "the
     * quest giver's sibling", not "your sibling". Saying "your" here told the player to look for their
     * <em>own</em> relative, which is a different villager entirely (usually none at all).
     */
    public Component describe() {
        return switch (mode) {
            case SELF -> Component.translatable("mcaquests.target.villager.self");
            case PROFESSION -> profession.map(DisplayNames::name)
                    .orElseGet(() -> Component.translatable("mcaquests.target.villager.someone"));
            case FAMILY -> Component.translatable("mcaquests.target.relation." + effectiveRelation());
            case SITUATION_FOCUS -> Component.translatable("mcaquests.target.villager.situation_focus");
            case UUID -> Component.translatable("mcaquests.target.villager.someone");
        };
    }

    /**
     * Like {@link #describe()} but resolves the concrete villager's actual <b>name</b> (and home village)
     * so the player knows who/where to find. Prefers a loaded target's display name; for an unloaded
     * {@code family} target it reads MCA's persistent name. Renders as {@code "Name (relation)"} (or just
     * {@code "Name"} for {@code self}), optionally suffixed with the home village.
     */
    public Component describeResolved(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return describeResolved(player, active, level, null);
    }

    /**
     * As {@link #describeResolved(ServerPlayer, ActiveQuest, ServerLevel)}, naming the {@code bound}
     * villager once the objective has locked one in — so the quest log keeps naming the same person even
     * while they are unloaded (the name comes from MCA's persistent family tree).
     */
    public Component describeResolved(ServerPlayer player, ActiveQuest active, ServerLevel level,
                                      @Nullable UUID bound) {
        Entity giver = level.getEntity(active.villagerUuid());
        Optional<LivingEntity> loaded = resolve(player, active, level, bound);
        Optional<String> name;
        Optional<String> village;
        if (loaded.isPresent()) {
            name = Optional.of(McaCompat.getVillagerDisplayName(loaded.get()).getString());
            village = McaCompat.getHomeVillageName(loaded.get());
        } else if (bound != null && giver != null) {
            name = McaCompat.getRelativeDisplayName(giver, bound);
            village = McaCompat.getHomeVillageName(giver);
        } else if (mode == Mode.FAMILY && giver != null) {
            name = selectRelative(giver, level).flatMap(u -> McaCompat.getRelativeDisplayName(giver, u));
            village = McaCompat.getHomeVillageName(giver); // a same-village relative shares the giver's village
        } else {
            name = Optional.empty();
            village = Optional.empty();
        }
        if (name.isEmpty() || name.get().isBlank()) {
            warnUnnamedFamilyTarget(active);
            return describe();
        }
        Component label = mode == Mode.SELF
                ? Component.literal(name.get())
                : Component.translatable("mcaquests.target.named", Component.literal(name.get()), describe());
        return village.filter(v -> !v.isBlank())
                .<Component>map(v -> Component.translatable("mcaquests.target.named_at", label, v))
                .orElse(label);
    }

    /**
     * A family target that has to fall back to "the quest giver's sibling" is not a cosmetic gap: after
     * the offer-time resolvability gate, a quest naming a relative who cannot be named should never have
     * been offered at all. Logged rather than thrown, because a relative can also legitimately disappear
     * mid-quest (that path shows a suspension reason instead), but it is worth a line in the log because
     * it means either MCA data went unreadable or a gate was skipped.
     */
    private void warnUnnamedFamilyTarget(ActiveQuest active) {
        if (mode == Mode.FAMILY) {
            McaQuests.LOGGER.debug("[MCA: Quests] Quest '{}' names a {} of its giver that cannot be "
                            + "resolved to anyone; falling back to the generic label.",
                    active.questId(), effectiveRelation());
        }
    }

    /** Cross-field validation surfaced by the owning objective's validator. */
    public void validate(String prefix, List<String> errors) {
        switch (mode) {
            case UUID -> {
                if (uuid.isEmpty()) {
                    errors.add(prefix + " uses villager mode 'uuid' but has no 'uuid'.");
                }
            }
            case PROFESSION -> {
                if (profession.isEmpty()) {
                    errors.add(prefix + " uses villager mode 'profession' but has no 'profession'.");
                }
            }
            case FAMILY -> {
                String rel = effectiveRelation();
                if (!RELATIONS.contains(rel)) {
                    errors.add(prefix + " uses unknown family relation '" + rel + "' (expected one of " + RELATIONS + ").");
                }
            }
            default -> {
            }
        }
        require.ifPresent(value -> {
            if (!RelativeCandidate.STATUSES.contains(value)) {
                errors.add(prefix + " uses unknown villager 'require' value '" + value + "' (expected one of "
                        + RelativeCandidate.STATUSES + ").");
            }
            if (mode != Mode.FAMILY) {
                errors.add(prefix + " sets 'require' on villager mode '" + mode.name().toLowerCase(Locale.ROOT)
                        + "', which already names exactly one villager; 'require' only applies to mode 'family'.");
            }
        });
    }

    private Optional<LivingEntity> resolveProfession(ServerPlayer player, @Nullable Entity giverEntity,
                                                    ServerLevel level) {
        if (profession.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation prof = profession.get();
        // Prefer a resident of the giver's home village...
        Optional<Entity> giver = Optional.ofNullable(giverEntity);
        if (giver.isPresent()) {
            OptionalInt villageId = McaCompat.getHomeVillageId(giver.get());
            if (villageId.isPresent()) {
                for (Entity e : McaCompat.loadedVillageResidents(level, villageId.getAsInt())) {
                    if (e instanceof LivingEntity le && matchesProfession(e, prof)) {
                        return Optional.of(le);
                    }
                }
            }
        }
        // ...otherwise the nearest loaded MCA villager of that profession around the player.
        AABB box = player.getBoundingBox().inflate(FALLBACK_SCAN_RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> McaCompat.isMcaVillager(e) && matchesProfession(e, prof)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
    }

    private static boolean matchesProfession(Entity entity, ResourceLocation profession) {
        return McaCompat.getProfessionId(entity).map(profession::equals).orElse(false);
    }

    private static Optional<Entity> giver(ServerLevel level, ActiveQuest active) {
        return Optional.ofNullable(level.getEntity(active.villagerUuid()));
    }

    private static Optional<LivingEntity> living(@Nullable Entity entity) {
        return entity instanceof LivingEntity le && McaCompat.isMcaVillager(le)
                ? Optional.of(le) : Optional.empty();
    }
}
