package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadBuildings;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadProfessionTrackView;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.TownsteadObjective;
import dev.otectus.mcaquests.quest.objective.TownsteadScheduleStreakObjective;
import dev.otectus.mcaquests.quest.target.FrozenLocation;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The small read-only summary of Townstead state shown beneath a quest in the log
 * (Townstead spec §8.3).
 *
 * <p>Built entirely on the server and sent as finished {@link Component}s. The client never reflects
 * on Townstead, never holds a Townstead type, and never treats Townstead's own client-side stores as
 * authoritative — it renders the strings it is given, so a client without Townstead installed shows
 * exactly what the server tells it.
 *
 * <p>The client option that hides this panel is applied when the log is <em>drawn</em>, not here.
 * A CLIENT config spec is not loaded on a dedicated server, so reading one from this method -- which
 * runs inside {@code syncLog} -- would throw the moment a player joined. Sending the lines and letting
 * each client decide whether to draw them is also what makes the option per-player rather than
 * server-wide, which is what a display preference should be.
 *
 * <p><b>Only what the quest is actually about.</b> Lines are chosen from the union of the capabilities
 * its Townstead objectives declare, so a quest about a villager's schedule does not display their
 * hunger, and a quest with no Townstead objectives at all shows nothing and takes no space. Reading
 * more than that would turn a helpful cue into a wall of numbers.
 */
public final class TownsteadContextLines {

    private TownsteadContextLines() {
    }

    /** The lines for one active quest, or empty when it has nothing Townstead-shaped to say. */
    public static List<Component> forQuest(ServerPlayer player, QuestDefinition def, ActiveQuest active) {
        if (!McaQuestsConfig.COMMON.townsteadEnabled.get()) {
            return List.of();
        }
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable() || !(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        Set<TownsteadCapability> relevant = capabilitiesOf(def);
        if (relevant.isEmpty()) {
            return List.of();
        }
        Entity giver = level.getEntity(active.villagerUuid());
        if (giver == null) {
            return List.of(); // nothing to report about somebody who is not here
        }

        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        TownsteadVillagerView view = evaluation.villager(giver).orElse(null);
        List<Component> lines = new ArrayList<>();

        if (view != null && relevant.contains(TownsteadCapability.READ_PROFESSION) && view.hasProfession()) {
            lines.add(Component.translatable("mcaquests.context.townstead.profession",
                    TownsteadNames.profession(view.professionId()), view.professionLevel()));
        }
        if (view != null && relevant.contains(TownsteadCapability.READ_PROFESSION_SPEC)
                && view.hasProfession()) {
            // How far this trade can go, next to where they are in it. Without the ceiling, "tier 2"
            // tells a player nothing about whether the quest is nearly done or barely started.
            TownsteadProfessionTrackView track = evaluation.professionTrack(view.professionId());
            if (track.progressive()) {
                lines.add(Component.translatable("mcaquests.context.townstead.track",
                        view.professionLevel(), track.maxTier(),
                        track.remainingXp(view.professionXp())));
            }
        }
        if (view != null && relevant.contains(TownsteadCapability.READ_NEEDS)) {
            lines.add(Component.translatable("mcaquests.context.townstead.needs",
                    view.needs().hunger(), view.needs().thirst(), view.needs().energy()));
        }
        if (view != null && relevant.contains(TownsteadCapability.READ_SCHEDULE)) {
            lines.add(Component.translatable("mcaquests.context.townstead.schedule",
                    TownsteadNames.activity(view.schedule().currentActivity()),
                    TownsteadNames.activity(view.schedule().plannedActivity())));
        }
        if (relevant.contains(TownsteadCapability.READ_SPIRIT)) {
            OptionalInt village = McaCompat.getHomeVillageId(giver);
            if (village.isPresent()) {
                evaluation.spirit(level, village.getAsInt()).ifPresent(spirit ->
                        lines.add(Component.translatable("mcaquests.context.townstead.spirit",
                                TownsteadNames.spirit(spirit.primaryId()), spirit.tier())));
            }
        }
        if (relevant.contains(TownsteadCapability.READ_BUILDING)) {
            OptionalInt village = McaCompat.getHomeVillageId(giver);
            if (village.isPresent()) {
                lines.add(Component.translatable("mcaquests.context.townstead.buildings",
                        evaluation.buildingsIn(level, village.getAsInt()).size()));
            }
            frozenBuilding(active).ifPresent(lines::add);
        }
        if (relevant.contains(TownsteadCapability.READ_CALENDAR)) {
            evaluation.calendar(player.getServer()).ifPresent(calendar -> {
                if (!calendar.season().isEmpty()) {
                    lines.add(Component.translatable("mcaquests.context.townstead.season",
                            calendar.season(), calendar.dayOfYear(), calendar.year()));
                }
            });
        }
        shiftProgress(def, active).ifPresent(lines::add);
        return List.copyOf(lines);
    }

    /**
     * "Shifts worked: 2 of 3", when the quest is about shifts at all.
     *
     * <p>Read off the objective's own progress rather than recomputed, so the card and the objective
     * line can never disagree about how far along a streak is.
     */
    private static java.util.Optional<Component> shiftProgress(QuestDefinition def, ActiveQuest active) {
        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i) instanceof TownsteadScheduleStreakObjective streak) {
                return java.util.Optional.of(Component.translatable(
                        "mcaquests.context.townstead.shifts",
                        active.progress(i).count(), streak.requiredShifts(),
                        TownsteadNames.activity(streak.activity())));
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * The building a frozen anchor settled on, so the card names the dock the quest actually means
     * rather than leaving the player to guess which of three it is.
     */
    private static java.util.Optional<Component> frozenBuilding(ActiveQuest active) {
        FrozenLocation location = active.anyFrozenBuilding();
        if (location == null || location.family().isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Component.translatable("mcaquests.context.townstead.building_target",
                TownsteadNames.building(location.family().get()),
                location.tier().orElse(1),
                location.pos().getX(), location.pos().getZ()));
    }

    /** Everything this quest's Townstead objectives read, and nothing else. */
    private static Set<TownsteadCapability> capabilitiesOf(QuestDefinition def) {
        EnumSet<TownsteadCapability> union = EnumSet.noneOf(TownsteadCapability.class);
        for (QuestObjective objective : def.objectives()) {
            if (objective instanceof TownsteadObjective townstead) {
                union.addAll(townstead.requiredCapabilities());
            }
        }
        return union;
    }
}
