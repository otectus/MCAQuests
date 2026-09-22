package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.data.StrictCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.DisplayNames;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.situation.QuestDefinitions;
import dev.otectus.mcaquests.quest.target.LocationAnchor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Talk to (interact with) MCA villagers of a given profession (spec section 14). MCA-specific; the
 * profession is read via {@code McaCompat} in the event handler.
 *
 * <p>{@code at_location_of} (1.7.0, optional) ties the conversation to the place another objective of
 * the same quest leads to — "find the next village and speak to whoever keeps <em>their</em> maps":
 *
 * <pre>{@code
 * { "type": "mcaquests:reach_location", "location": { "anchor": "nearest_other_village" } },
 * { "type": "mcaquests:talk_to_profession", "profession": "minecraft:cartographer", "at_location_of": 0 }
 * }</pre>
 *
 * <p>The index names a sibling objective with a location — {@code reach_location}, {@code escort_entity},
 * {@code build_near_location} or {@code defend_location} — and uses that objective's <em>frozen</em>
 * destination, so it never re-binds as the player travels. A villager then counts when they live in the
 * destination village, or stand within MCA's villager margin of it; for a destination that is not a
 * village, within the sibling's own radius. The same test decides both credit and the guidance marker,
 * so a marker never points at somebody the objective would refuse. Without the field nothing changes.
 */
public record TalkToProfessionObjective(ResourceLocation profession, int count, Optional<Integer> atLocationOf)
        implements QuestObjective {

    /** MCA's own margin for "a villager is in the village", used for a destination village. */
    static final int VILLAGE_MARGIN = 48;
    /** How close to a non-village destination a villager must be, when the sibling states no radius. */
    static final int DEFAULT_RADIUS = 32;

    public static final Codec<TalkToProfessionObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("profession").forGetter(TalkToProfessionObjective::profession),
            StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1).forGetter(TalkToProfessionObjective::count),
            StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "at_location_of")
                    .forGetter(TalkToProfessionObjective::atLocationOf)
    ).apply(instance, TalkToProfessionObjective::new));

    /** The pre-1.7.0 shape: anywhere. */
    public TalkToProfessionObjective(ResourceLocation profession, int count) {
        this(profession, count, Optional.empty());
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TALK_TO_PROFESSION;
    }

    @Override
    public Component describe() {
        return Component.translatable(atLocationOf.isPresent()
                        ? "mcaquests.objective.talk_to_profession_there"
                        : "mcaquests.objective.talk_to_profession", count,
                DisplayNames.name(profession));
    }

    /**
     * The nearest villager of the profession the player has not already spoken to and who would count.
     *
     * <p>"Speak with three of our farmers" is only obvious in a village the player already knows, and
     * the objective deliberately counts <em>distinct</em> villagers — so the useful marker is not any
     * farmer, it is one who does not count yet. {@code progress.hasTalkedTo} already remembers who
     * does, so this simply skips them and the marker steps to the next one as each is talked to. With
     * {@code at_location_of} it also skips anybody outside the destination, by the same test credit uses.
     *
     * <p>Scans loaded entities around the player only. A villager who is not loaded cannot be talked
     * to either, so there is nothing to point at and nothing is pointed at.
     */
    @Override
    public Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return Optional.empty();
        }
        Optional<Destination> destination = destination(player, active, level);
        if (atLocationOf.isPresent() && destination.isEmpty()) {
            return Optional.empty(); // the destination is not known yet: point at nobody rather than anybody
        }
        return level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(SEARCH_RADIUS),
                        e -> McaCompat.isMcaVillager(e)
                                && !progress.hasTalkedTo(e.getUUID())
                                && matches(McaCompat.getProfessionId(e).orElse(null))
                                && destination.map(d -> d.accepts(level, e)).orElse(true))
                .stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .map(villager -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofEntity(villager,
                        dev.otectus.mcaquests.quest.guidance.GuidanceKind.VILLAGER,
                        McaCompat.getVillagerDisplayName(villager)));
    }

    /** Blocks around the player to look in. Beyond loaded range there is nobody to talk to anyway. */
    private static final double SEARCH_RADIUS = 64.0D;

    @Override
    public int required() {
        return count;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), count);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /**
     * Profession match under the configured {@code professionMatchingMode}, so a datapack asking for
     * {@code minecraft:cartographer} still matches an MCA villager whose profession id is namespaced
     * differently (the default {@code NORMALIZED} mode ignores the namespace).
     */
    public boolean matches(@Nullable ResourceLocation talkedToProfession) {
        return ProfessionMatcher.matches(profession, talkedToProfession);
    }

    /**
     * True when a conversation with {@code villager} is in the right place for this objective. Always
     * true without {@code at_location_of}; otherwise false until the destination is known.
     */
    public boolean acceptsPlace(ServerPlayer player, ActiveQuest active, ServerLevel level, Entity villager) {
        if (atLocationOf.isEmpty()) {
            return true;
        }
        return destination(player, active, level).map(d -> d.accepts(level, villager)).orElse(false);
    }

    /** The sibling objective's frozen destination, when this objective names one and it has resolved. */
    Optional<Destination> destination(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        if (atLocationOf.isEmpty()) {
            return Optional.empty();
        }
        Optional<QuestDefinition> def = QuestDefinitions.resolve(active.questId()).map(active::resolve);
        if (def.isEmpty() || atLocationOf.get() >= def.get().objectives().size()) {
            return Optional.empty();
        }
        QuestObjective sibling = def.get().objectives().get(atLocationOf.get());
        return anchorOf(sibling).flatMap(anchor -> anchor.location().resolveTarget(player, active, level)
                .map(resolved -> new Destination(resolved.villageId(), resolved.pos(), anchor.radius())));
    }

    /** A sibling's location and how near it counts, for the objective types that have one. */
    public record SiblingAnchor(LocationAnchor location, int radius) {
    }

    public static Optional<SiblingAnchor> anchorOf(QuestObjective sibling) {
        if (sibling instanceof ReachLocationObjective reach) return Optional.of(new SiblingAnchor(reach.location(), reach.radius()));
        if (sibling instanceof EscortEntityObjective escort) return Optional.of(new SiblingAnchor(escort.destination(), DEFAULT_RADIUS));
        if (sibling instanceof BuildNearLocationObjective build) return Optional.of(new SiblingAnchor(build.location(), DEFAULT_RADIUS));
        if (sibling instanceof DefendLocationObjective defend) return Optional.of(new SiblingAnchor(defend.location(), DEFAULT_RADIUS));
        return Optional.empty();
    }

    /** A resolved destination and the test a villager must pass to count there. */
    record Destination(OptionalInt villageId, net.minecraft.core.BlockPos pos, int radius) {
        boolean accepts(ServerLevel level, Entity villager) {
            if (villageId.isPresent()) {
                OptionalInt home = McaCompat.getHomeVillageId(villager);
                return (home.isPresent() && home.getAsInt() == villageId.getAsInt())
                        || McaCompat.isWithinVillage(level, villageId.getAsInt(), villager.blockPosition(), VILLAGE_MARGIN);
            }
            int r = Math.max(radius, 1);
            return villager.blockPosition().distSqr(pos) <= (long) r * r;
        }
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        atLocationOf.ifPresent(target -> {
            if (target == index) {
                errors.add("Quest '" + questId + "': objective[" + index + "] at_location_of points at itself");
            }
        });
    }
}
