package dev.otectus.mcaquests.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Townstead reads for this same pass, created only if something actually asks (Townstead spec 4.1).
     * It lives here rather than on {@code QuestContext} because this snapshot is already the per-pass
     * object every condition shares, so hanging it here gives the caching for free and leaves every
     * existing {@code QuestContext} construction site untouched.
     */
    @Nullable
    private TownsteadEvaluation townstead;

    private final Map<String, Boolean> familyMemo = new HashMap<>();
    /**
     * One candidate list per relation for the whole pass.
     *
     * <p>This is what keeps the new offer-time resolvability check affordable. Every villager-targeted
     * quest in the pool asks about the giver's family, and building a candidate list walks the family
     * tree and reads the village rolls; without this the cost would be multiplied by the size of the
     * catalogue on every single villager interaction.
     */
    private final Map<String, List<RelativeCandidate>> candidateMemo = new HashMap<>();

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

    /** The pass's Townstead reads. Empty of everything until the first Townstead condition asks. */
    public TownsteadEvaluation townstead() {
        if (townstead == null) {
            townstead = new TownsteadEvaluation();
        }
        return townstead;
    }

    /**
     * Lazily resolves + memoizes whether the villager is {@code relation} <em>to the player</em>. Not the
     * same question as {@link #relativeCandidates}, which asks who the villager's own relatives are.
     */
    public boolean isFamilyOfPlayer(String relation) {
        return familyMemo.computeIfAbsent(relation, r -> McaCompat.isFamilyOfPlayer(player, villager, r));
    }

    /**
     * Lazily resolves + memoizes the villager's relatives of {@code relation}.
     *
     * <p>The one list the gate, the offer-time resolvability check and the display name all filter, so
     * within a pass they are guaranteed to be reasoning about exactly the same people.
     */
    public List<RelativeCandidate> relativeCandidates(String relation) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return candidateMemo.computeIfAbsent(relation,
                r -> McaCompat.relativeCandidates(level, villager, r));
    }

    /** Whether any relative of {@code relation} matches {@code status} — a filter over the memoized list. */
    public boolean relativesWithStatus(String relation, String status) {
        for (RelativeCandidate candidate : relativeCandidates(relation)) {
            if (candidate.matches(status)) {
                return true;
            }
        }
        return false;
    }
}
