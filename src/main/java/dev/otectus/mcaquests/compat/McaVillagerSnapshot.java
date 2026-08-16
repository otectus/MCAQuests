package dev.otectus.mcaquests.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Per-eligibility-pass snapshot of an MCA villager's state for the interacting player (spec/Phase 1
 * §3). Built once in {@code QuestManager.eligibleOffers()} and shared across every condition in that
 * pass, so a villager's MCA state is read <em>once per pass</em> rather than once per condition.
 *
 * <p>Cheap per-villager fields are captured eagerly in the constructor. The heavier family-graph
 * queries ({@link #isFamilyOfPlayer}, {@link #relativesWithStatus}) are computed lazily on first use
 * and memoized, so they only cost anything when a quest actually uses one of those conditions.
 *
 * <p>This class contains <b>no MCA imports</b>: it only delegates to {@link McaCompat}, which is the
 * single MCA boundary. All values are already fail-safe defaults when MCA data is unavailable.
 */
public final class McaVillagerSnapshot {

    private final ServerPlayer player;
    private final Entity villager;

    private final boolean mcaVillager;
    private final boolean playerSpouse;
    private final Optional<String> relationshipState;
    private final Optional<String> ageGroup;
    private final Optional<String> personality;
    private final OptionalInt moodValue;
    private final Optional<String> moodName;
    private final boolean homeVillage;
    private final Optional<BlockPos> homeVillageCenter;
    private final boolean home;
    private final OptionalDouble healthFraction;
    private final float infectionProgress;

    private final Map<String, Boolean> familyMemo = new HashMap<>();
    private final Map<String, Boolean> relativeStatusMemo = new HashMap<>();

    public McaVillagerSnapshot(ServerPlayer player, Entity villager) {
        this.player = player;
        this.villager = villager;
        this.mcaVillager = McaCompat.isMcaVillager(villager);
        this.playerSpouse = McaCompat.isPlayerSpouse(player, villager);
        this.relationshipState = McaCompat.getRelationshipState(villager);
        this.ageGroup = McaCompat.getAgeStateName(villager);
        this.personality = McaCompat.getPersonalityName(villager);
        this.moodValue = McaCompat.getMoodValue(villager);
        this.moodName = McaCompat.getMoodName(villager);
        this.homeVillage = McaCompat.hasHomeVillage(villager);
        this.homeVillageCenter = McaCompat.getHomeVillageCenter(villager);
        this.home = McaCompat.hasHome(villager);
        this.healthFraction = McaCompat.getHealthFraction(villager);
        this.infectionProgress = McaCompat.getInfectionProgress(villager);
    }

    public boolean isMcaVillager() {
        return mcaVillager;
    }

    public boolean isPlayerSpouse() {
        return playerSpouse;
    }

    public Optional<String> relationshipState() {
        return relationshipState;
    }

    public Optional<String> ageGroup() {
        return ageGroup;
    }

    public Optional<String> personality() {
        return personality;
    }

    public OptionalInt moodValue() {
        return moodValue;
    }

    public Optional<String> moodName() {
        return moodName;
    }

    public boolean hasHomeVillage() {
        return homeVillage;
    }

    /** The center of the giver's MCA home village (captured once per pass), or empty when it has none. */
    public Optional<BlockPos> homeVillageCenter() {
        return homeVillageCenter;
    }

    public boolean hasHome() {
        return home;
    }

    public OptionalDouble healthFraction() {
        return healthFraction;
    }

    public float infectionProgress() {
        return infectionProgress;
    }

    /** Lazily resolves + memoizes whether the villager is {@code relation} to the player. */
    public boolean isFamilyOfPlayer(String relation) {
        return familyMemo.computeIfAbsent(relation, r -> McaCompat.isFamilyOfPlayer(player, villager, r));
    }

    /** Lazily resolves + memoizes whether a relative of {@code relation} matches {@code status}. */
    public boolean relativesWithStatus(String relation, String status) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        return relativeStatusMemo.computeIfAbsent(relation + '/' + status,
                key -> McaCompat.relativesWithStatus(level, villager, relation, status));
    }
}
