package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.objective.ObjectiveSupport;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Translates world state into {@link TriggerSignal}s for {@link SituationManager} (the "Living Village"
 * phase, 0.8.0). Two entry points:
 *
 * <ul>
 *   <li>{@link #onVillagerDeath} — event-driven, called from the {@code LivingDeathEvent} hook.</li>
 *   <li>{@link #scan} — the periodic sweep of villages near online players for tick-driven conditions
 *       (active raid, low food, nightfall, infected residents, missing kin).</li>
 * </ul>
 *
 * <p>Detection is intentionally player-proximity-driven: situations only matter where players are, and
 * this bounds the per-tick work. All MCA access goes through {@link McaCompat}; moon/night math is plain
 * vanilla. The throttle in {@link SituationManager} keeps repeated signals from spamming situations.
 */
public final class SituationDetectors {

    /** How far from a player to look for a village to scan. */
    private static final int DETECTION_RADIUS = 128;

    /**
     * Thresholds for the two MCA-only detectors (spec 10.3). The holds are counted in <em>sweeps</em>
     * rather than ticks because that is the unit this scan actually has; two sightings is enough to
     * tell a villager who is stranded from one who was walking past.
     */
    private static final int STRANDED_DISTANCE = 96;
    private static final int STRANDED_HOLD_SCANS = 2;
    private static final int HOSTILE_COUNT = 3;
    private static final int HOSTILE_RADIUS = 16;
    private static final int HOSTILE_HOLD_SCANS = 2;

    private SituationDetectors() {
    }

    /** Emits a {@code villager_death} signal for an MCA villager that just died (if it has a village). */
    public static void onVillagerDeath(MinecraftServer server, ServerLevel level, Entity dead) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return;
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(dead);
        if (villageId.isEmpty()) {
            return;
        }
        UUID familyRoot = McaCompat.getFamilyRootId(dead).orElse(null);
        SituationManager.onSignal(server,
                TriggerSignal.villagerDeath(level, villageId.getAsInt(), dead.getUUID(), familyRoot));
    }

    /** Sweeps every village near an online player once, emitting any tick-driven signals it detects. */
    public static void scan(MinecraftServer server) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return;
        }
        Set<String> scanned = new HashSet<>();
        int budget = McaQuestsConfig.COMMON.townsteadMaxVillagesPerPass.get();
        long rotation = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (scanned.size() >= budget) {
                break; // bounded per pass; the players not reached this time are reached the next
            }
            ServerLevel level = player.serverLevel();
            OptionalInt villageId = McaCompat.findNearestVillageId(level, player.blockPosition(), DETECTION_RADIUS);
            if (villageId.isEmpty()) {
                continue;
            }
            String key = level.dimension().location() + "#" + villageId.getAsInt();
            if (scanned.add(key)) {
                scanVillage(server, level, villageId.getAsInt());
                TownsteadSituationDetector.scanVillage(server, level, villageId.getAsInt(), rotation);
            }
        }
    }

    private static void scanVillage(MinecraftServer server, ServerLevel level, int villageId) {
        McaCompat.villageCenter(level, villageId).ifPresent(center -> {
            if (McaCompat.isRaidActive(level, center)) {
                SituationManager.onSignal(server, TriggerSignal.raid(level, villageId));
            }
        });
        McaCompat.getVillageFoodCount(level, villageId).ifPresent(food ->
                SituationManager.onSignal(server, TriggerSignal.lowFood(level, villageId, food)));
        if (isNight(level)) {
            SituationManager.onSignal(server, TriggerSignal.night(level, villageId, isFullMoon(level)));
        }

        UUID infectedVillager = null;
        float infectionProgress = 0f;
        UUID missingKinRoot = null;
        UUID missingKinVillager = null;
        for (Entity resident : McaCompat.loadedVillageResidents(level, villageId)) {
            if (infectedVillager == null) {
                float progress = McaCompat.getInfectionProgress(resident);
                if (progress > 0f) {
                    infectedVillager = resident.getUUID();
                    infectionProgress = progress;
                }
            }
            if (missingKinRoot == null && McaCompat.hasMissingRelative(level, resident)) {
                missingKinRoot = McaCompat.getFamilyRootId(resident).orElse(resident.getUUID());
                missingKinVillager = resident.getUUID();
            }
            if (infectedVillager != null && missingKinRoot != null) {
                break;
            }
        }
        if (infectedVillager != null) {
            SituationManager.onSignal(server,
                    TriggerSignal.infection(level, villageId, infectedVillager, infectionProgress));
        }
        if (missingKinRoot != null) {
            SituationManager.onSignal(server, TriggerSignal.missingKin(level, villageId,
                    missingKinVillager, missingKinRoot));
        }
        if (wants(SituationSignalType.VILLAGER_STRANDED)) {
            scanStranded(server, level, villageId);
        }
        if (wants(SituationSignalType.HOSTILES_NEAR_HOME)) {
            scanHostilesNearHome(server, level, villageId);
        }
    }

    /** True when some loaded definition actually consumes this signal; nothing else costs anything. */
    private static boolean wants(SituationSignalType type) {
        return SituationRegistry.all().stream()
                .filter(SituationDefinition::enabled)
                .anyMatch(def -> def.trigger().signalType() == type);
    }

    /**
     * Finds a resident who is a long way outside their own village after dark and has stayed there
     * (spec 5.8).
     *
     * <p>Uses MCA's village border and residency only, so this works — and its situations play —
     * on an install that has never had Townstead.
     *
     * <p>Two consecutive sightings are required before it fires, held in
     * {@link TownsteadSignalStateSavedData} under a per-villager key. A villager who stepped past the
     * border for a moment is not stranded, and without the hold every evening would produce a stream
     * of rescues nobody needs. The hold is cleared the moment they are home again, so coming back is
     * not remembered as a near-miss.
     */
    private static void scanStranded(MinecraftServer server, ServerLevel level, int villageId) {
        boolean night = isNight(level);
        BlockPos centre = McaCompat.villageCenter(level, villageId).orElse(null);
        if (centre == null) {
            return;
        }
        TownsteadSignalStateSavedData state = TownsteadSignalStateSavedData.get(server);
        for (Entity resident : McaCompat.loadedVillageResidents(level, villageId)) {
            String key = resident.getUUID() + "|stranded";
            boolean outside = !McaCompat.isWithinVillage(level, villageId, resident.blockPosition());
            int distance = outside ? (int) Math.sqrt(centre.distSqr(resident.blockPosition())) : 0;
            if (!outside || !night || distance < STRANDED_DISTANCE) {
                state.observeChanged(key, 0);
                continue;
            }
            int held = state.lastReading(key, 0) + 1;
            state.observeChanged(key, held);
            if (held < STRANDED_HOLD_SCANS) {
                continue;
            }
            state.observeChanged(key, 0); // fired: start counting again rather than firing every scan
            SituationManager.onSignal(server,
                    TriggerSignal.villagerStranded(level, villageId, resident.getUUID(), distance, true));
            return; // one rescue at a time; the next scan finds the next one
        }
    }

    /**
     * Counts hostile mobs gathered around a resident's bed, falling back to the village centre for a
     * resident with no home (spec 5.8).
     *
     * <p><b>Bounded on purpose.</b> The obvious implementation — sweep the dimension for hostiles — is
     * the single most expensive thing this mod could do, and it would do it on every server forever.
     * Querying a small box around a place that already matters costs almost nothing and finds exactly
     * the mobs a player would care about.
     */
    private static void scanHostilesNearHome(MinecraftServer server, ServerLevel level, int villageId) {
        TownsteadSignalStateSavedData state = TownsteadSignalStateSavedData.get(server);
        for (Entity resident : McaCompat.loadedVillageResidents(level, villageId)) {
            BlockPos home = McaCompat.getHomePos(resident).orElse(resident.blockPosition());
            AABB box = new AABB(home).inflate(HOSTILE_RADIUS);
            int hostiles = 0;
            for (Entity nearby : level.getEntities((Entity) null, box, ObjectiveSupport::isHostile)) {
                if (nearby.isAlive()) {
                    hostiles++;
                }
            }
            String key = resident.getUUID() + "|hostiles";
            if (hostiles < HOSTILE_COUNT) {
                state.observeChanged(key, 0);
                continue;
            }
            int held = state.lastReading(key, 0) + 1;
            state.observeChanged(key, held);
            if (held < HOSTILE_HOLD_SCANS) {
                continue;
            }
            state.observeChanged(key, 0);
            SituationManager.onSignal(server,
                    TriggerSignal.hostilesNearHome(level, villageId, resident.getUUID(), hostiles));
            return; // one alarm per sweep, so a bad night does not open six situations at once
        }
    }

    /** Dusk-to-dawn window (vanilla time-of-day 13000..23000). */
    private static boolean isNight(ServerLevel level) {
        long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
        return timeOfDay >= 13000L && timeOfDay < 23000L;
    }

    /** Vanilla moon phase 0 (full moon). */
    private static boolean isFullMoon(ServerLevel level) {
        return Math.floorMod(Math.floorDiv(level.getDayTime(), 24000L), 8L) == 0L;
    }
}
