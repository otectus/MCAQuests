package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadResidentRecordView;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What the two resident-wellbeing objectives are allowed to count as a reading of a villager.
 *
 * <p>Two sources, in strict order of trust.
 *
 * <ol>
 *   <li><b>Live</b>: a loaded villager, read through the bridge. Always used, always first.</li>
 *   <li><b>Last known</b>: what Townstead's resident register recorded the last time it saw a
 *       villager who is not loaded now (Townstead 0.8 and later; 0.7.x keeps no register). Used
 *       only when the definition opts in with {@code last_known_max_age_days}, and only for a
 *       record that is fresh enough, alive, and filed under the village being judged.</li>
 * </ol>
 *
 * <p>The default is off, so a definition written for the loaded-only rule keeps exactly that rule:
 * {@code minimum_loaded_fraction} is still measured against <em>loaded</em> residents, never
 * against how many happen to have a record, and a stale record can never stand in for a villager
 * the check ought to be looking at. What the opt-in buys is a wider denominator: a village of
 * forty with twenty loaded is judged on forty rather than twenty, using readings no older than the
 * pack author accepted. A reading is as old as the register says, because needs do not advance
 * while a villager is unloaded -- a villager last seen ten days ago at full hunger is not evidence
 * they are fed today, only that they were then.
 */
public final class TownsteadResidentEvidence {

    /** Records are consulted at most this many per poll, so a pathological roll stays bounded. */
    static final int MAX_RECORDS_PER_POLL = 256;

    private TownsteadResidentEvidence() {
    }

    /**
     * Whether one last-known record may stand in for an unloaded villager of the given village.
     *
     * @param record           Townstead's record, already known to be for a villager who is not loaded
     * @param villageDimension the dimension the judged village lives in
     * @param villageId        the judged village
     * @param worldDay         Townstead's current world day, the clock the record's age is measured on
     * @param maxAgeDays       the oldest reading the definition accepts; {@code 0} accepts none
     */
    public static boolean acceptable(TownsteadResidentRecordView record, @Nullable ResourceLocation villageDimension,
                                     int villageId, long worldDay, int maxAgeDays) {
        if (record == null || maxAgeDays <= 0) {
            return false;
        }
        if (!record.alive() || !record.belongsTo(villageDimension, villageId)) {
            return false;
        }
        long age = worldDay - record.lastSeenWorldDay();
        return age >= 0 && age <= maxAgeDays;
    }

    /**
     * The readings for a village: every loaded resident handed in, read live, then -- when the
     * definition opts in -- the last-known record of every other resident on MCA's roll that
     * {@link #acceptable} admits. No villager is counted twice, and a villager whose live read
     * failed is <em>not</em> replaced by their record: an unreadable villager is missing, not healthy.
     *
     * @param loaded        the loaded residents this pass is judging (already windowed by the caller)
     * @param roll          MCA's full resident roll for the village
     * @param maxAgeDays    {@code last_known_max_age_days} from the definition
     * @return the readings, live first, and how many of them were live
     */
    public static Readings readings(ServerLevel level, int villageId, List<Entity> loaded, Set<UUID> roll,
                                    int maxAgeDays) {
        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        List<TownsteadNeedsView> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Entity resident : loaded) {
            if (!seen.add(resident.getUUID())) {
                continue;
            }
            TownsteadVillagerView view = evaluation.villager(resident).orElse(null);
            if (view != null) {
                out.add(view.needs());
            }
        }
        int live = out.size();
        if (maxAgeDays <= 0 || roll.isEmpty()) {
            return new Readings(out, live);
        }
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        long worldDay = evaluation.calendar(level.getServer()).map(calendar -> calendar.worldDay()).orElse(-1L);
        if (worldDay < 0) {
            return new Readings(out, live); // no clock to measure age on: no record is fresh enough
        }
        ResourceLocation dimension = level.dimension().location();
        int consulted = 0;
        for (UUID uuid : roll) {
            if (seen.contains(uuid) || consulted >= MAX_RECORDS_PER_POLL) {
                continue;
            }
            consulted++;
            TownsteadResidentRecordView record = bridge.lastKnownResident(level.getServer(), uuid).orElse(null);
            if (record == null || record.loaded()) {
                continue; // loaded but not in this pass's window: the rotating window reaches them live
            }
            if (acceptable(record, dimension, villageId, worldDay, maxAgeDays)) {
                seen.add(uuid);
                out.add(record.needs());
            }
        }
        return new Readings(out, live);
    }

    /** True when the giver's home village could be resolved; convenience for the personal objective. */
    public static java.util.OptionalInt villageOf(@Nullable Entity giver) {
        return giver == null ? java.util.OptionalInt.empty() : McaCompat.getHomeVillageId(giver);
    }

    /** The readings gathered for one poll, live ones first. */
    public record Readings(List<TownsteadNeedsView> needs, int live) {
        public int size() {
            return needs.size();
        }
    }
}
