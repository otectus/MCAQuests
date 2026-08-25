package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Reach a {@link LocationAnchor} on foot: sticks complete once the PLAYER reaches the resolved anchor.
 * Arrival is border-aware — for a village anchor the player need only be inside the village border; for
 * every other anchor it is a horizontal (Y-ignored) distance within {@code radius}. Polled once per second
 * by {@code QuestProgressEvents}. An unresolved anchor (an unloaded giver/village) simply pauses the
 * objective — it never false-completes. Distinct from {@code enter_structure} (which keys off a named
 * structure) and from {@code escort_entity} (which moves a villager rather than tracking the player).
 */
public record ReachLocationObjective(LocationAnchor location, int radius,
                                     Optional<Integer> minJourney) implements QuestObjective {

    public static final Codec<ReachLocationObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LocationAnchor.MAP_CODEC.fieldOf("location").forGetter(ReachLocationObjective::location),
            Codec.intRange(1, 64).optionalFieldOf("radius", 6).forGetter(ReachLocationObjective::radius),
            Codec.intRange(0, 512).optionalFieldOf("min_journey").forGetter(ReachLocationObjective::minJourney)
    ).apply(instance, ReachLocationObjective::new));

    /** How far the player must start from the anchor, defaulting to {@code minEscortJourney}. */
    public int effectiveMinJourney() {
        return ObjectiveSupport.effectiveMinJourney(minJourney);
    }

    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        return location.resolveTarget(context.player(), context.villager(), context.level())
                .map(dest -> ObjectiveSupport.withinJourneyOf(context.player(), context.level(), dest,
                        radius, effectiveMinJourney()))
                .orElse(false);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.REACH_LOCATION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.reach_location", location.describe());
    }

    @Override
    public int required() {
        return 1;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), 1);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= 1;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /**
     * Per-second poll: stick complete once the player reaches the anchor (inside the village border, or
     * within radius) — but only once the journey has actually been made. A quest granted outside the
     * offer pipeline (a chain stage, a command) can land with the player already standing on the
     * destination, and crediting that would hand over the reward for nothing; see
     * {@link ObjectiveSupport#isJourneyArmed}.
     */
    public void poll(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (progress.count() >= 1) {
            return;
        }
        location.resolveTarget(player, active, level).ifPresent(resolved -> {
            if (!ObjectiveSupport.isJourneyArmed(progress, active, level, player, resolved,
                    radius, effectiveMinJourney())) {
                return;
            }
            boolean arrived = resolved.villageId().isPresent()
                    && McaCompat.villageExists(level, resolved.villageId().getAsInt())
                    ? McaCompat.isWithinVillage(level, resolved.villageId().getAsInt(), player.blockPosition())
                    : ObjectiveSupport.withinRadiusXZ(player, resolved.pos(), radius);
            if (arrived) {
                progress.setCount(1);
            }
        });
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        location.validate("Quest '" + questId + "': objective[" + index + "] location", errors);
    }
}
