package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.DisplayNames;
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
 * { "mode": "family", "relation": "sibling" }            // any|spouse|parent|child|sibling
 * { "mode": "uuid", "uuid": "&lt;uuid&gt;" }
 * </pre>
 */
public record VillagerTarget(Mode mode, Optional<ResourceLocation> profession,
                             Optional<String> relation, Optional<UUID> uuid) {

    public enum Mode {
        SELF, PROFESSION, FAMILY, UUID
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
            UUIDUtil.STRING_CODEC.optionalFieldOf("uuid").forGetter(VillagerTarget::uuid)
    ).apply(instance, VillagerTarget::new));

    public static final Codec<VillagerTarget> CODEC = MAP_CODEC.codec();

    /** The default target: the quest giver. */
    public static final VillagerTarget SELF =
            new VillagerTarget(Mode.SELF, Optional.empty(), Optional.empty(), Optional.empty());

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
        return resolveFrom(player, level.getEntity(active.villagerUuid()), level);
    }

    /**
     * As {@link #resolve(ServerPlayer, ActiveQuest, ServerLevel)}, but keyed on the giver entity rather
     * than on an accepted quest — every mode resolves <em>relative to the giver</em>, and the
     * {@link ActiveQuest} was only ever used to look that giver up.
     *
     * <p>This is what lets an objective answer "would I already be satisfied?" while a quest is merely
     * being <em>offered</em>, when no {@code ActiveQuest} exists yet — the check that stops a quest being
     * offered in a state where it could be turned straight back in for the reward.
     */
    public Optional<LivingEntity> resolveFrom(ServerPlayer player, @Nullable Entity giver, ServerLevel level) {
        return switch (mode) {
            case SELF -> living(giver);
            case UUID -> uuid.flatMap(u -> living(level.getEntity(u)));
            case FAMILY -> Optional.ofNullable(giver)
                    .flatMap(g -> McaCompat.findGiverRelative(level, g, relation.orElse("any")))
                    .flatMap(u -> living(level.getEntity(u)));
            case PROFESSION -> resolveProfession(player, giver, level);
        };
    }

    /** True when {@code candidate} is the villager this target refers to (for interaction/event credit). */
    public boolean matches(LivingEntity candidate, ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return matches(candidate, player, active, level, null);
    }

    /**
     * As {@link #matches(LivingEntity, ServerPlayer, ActiveQuest, ServerLevel)}, but exact once a
     * {@code bound} villager has been locked in. Without a binding, {@code family} mode credits <em>any</em>
     * relative of that relation, so a parcel meant for one sibling could be handed to another; with one, only
     * the villager the quest actually named counts.
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
            case FAMILY -> giver(level, active)
                    .map(g -> McaCompat.giverRelativeUuids(level, g, relation.orElse("any"))
                            .contains(candidate.getUUID()))
                    .orElse(false);
        };
    }

    /**
     * A generic label for this target, used when no concrete villager has resolved yet.
     *
     * <p>{@code family} relations are resolved <em>relative to the quest giver</em> (see
     * {@link McaCompat#findGiverRelative}), never to the player, so the {@code mcaquests.target.relation.*}
     * strings must read from the giver's perspective — "the quest giver's sibling", not "your sibling".
     * Saying "your" here told the player to look for their <em>own</em> relative, which is a different
     * villager entirely (usually none at all).
     */
    public Component describe() {
        return switch (mode) {
            case SELF -> Component.translatable("mcaquests.target.villager.self");
            case PROFESSION -> profession.map(DisplayNames::name)
                    .orElseGet(() -> Component.translatable("mcaquests.target.villager.someone"));
            case FAMILY -> Component.translatable("mcaquests.target.relation." + relation.orElse("any"));
            case UUID -> Component.translatable("mcaquests.target.villager.someone");
        };
    }

    /**
     * Like {@link #describe()} but resolves the concrete villager's actual <b>name</b> (and home village)
     * so the player knows who/where to find. Prefers a loaded target's display name; for an unloaded
     * {@code family} target it reads MCA's persistent name. Renders as {@code "Name (relation)"} (or just
     * {@code "Name"} for {@code self}), optionally suffixed with the home village. Falls back to the generic
     * {@link #describe()} when no name resolves (non-MCA giver, no such relative, missing family data).
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
            name = McaCompat.findGiverRelative(level, giver, relation.orElse("any"))
                    .flatMap(u -> McaCompat.getRelativeDisplayName(giver, u));
            village = McaCompat.getHomeVillageName(giver); // a same-village relative shares the giver's village
        } else {
            name = Optional.empty();
            village = Optional.empty();
        }
        if (name.isEmpty() || name.get().isBlank()) {
            return describe();
        }
        Component label = mode == Mode.SELF
                ? Component.literal(name.get())
                : Component.translatable("mcaquests.target.named", Component.literal(name.get()), describe());
        return village.filter(v -> !v.isBlank())
                .<Component>map(v -> Component.translatable("mcaquests.target.named_at", label, v))
                .orElse(label);
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
                String rel = relation.orElse("any");
                if (!RELATIONS.contains(rel)) {
                    errors.add(prefix + " uses unknown family relation '" + rel + "' (expected one of " + RELATIONS + ").");
                }
            }
            default -> {
            }
        }
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
