package dev.otectus.mcaquests.project.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A player-targeted reward owed to a player who is not in a position to receive it right now.
 * Tagged union over NBT via an optional {@code kind} discriminator (task M3.1, spec 1.0.0 §16):
 *
 * <ul>
 *   <li><b>{@code kind} key absent</b> — legacy default, {@link Kind#PROJECT_PHASE}: a community-project
 *       phase reward owed to a player who was offline when the phase completed. Stores only coordinates
 *       into the definition (project + phase + reward index), so no {@code QuestReward} needs
 *       serialising. This is the pre-1.0.0 shape verbatim: {@link #save()} writes no {@code kind} key for
 *       this kind, so a 0.9.x world's pending list still loads byte-identical (same "project"/"phase"/
 *       "reward" keys, same coordinates) after upgrading.
 *   <li><b>{@code kind = "banked"}</b> — {@link Kind#BANKED}: an FTB Quests reward claim
 *       (village_reputation / hearts / grant_title) that had no resolvable target (no village / spouse /
 *       villager nearby) at claim time. Carries a {@link BankedReward} payload and is retried by
 *       {@code ProjectManager.deliverPending} on login and once per in-game day. This class (and its
 *       payload) stay FTB-agnostic: FTB-side target enums are stored as plain strings on
 *       {@link BankedReward#target()}, never as FTB types.
 * </ul>
 *
 * <p><b>Forward-compat guard:</b> a {@code kind} value this loader doesn't recognise — e.g. a save
 * written by a later version this build predates — makes {@link #load} return {@link Optional#empty()}
 * rather than throw, so an unknown future kind is skipped without corrupting sibling entries in the same
 * list (verified in {@code ProjectStateTest}).
 *
 * <p><b>Backward-compat finding (task M3.1):</b> the pre-1.0.0 {@code PendingReward.load} reads only
 * "project" (via {@code ResourceLocation}, NBT-default {@code ""}), "phase" and "reward" (NBT-default
 * {@code 0}), with no validation. {@code ResourceLocation.isValidPath} accepts the empty string, so a
 * "banked" entry (which has neither key) degrades there to an inert
 * {@code ResourceLocation("minecraft", "")} / phase 0 / reward 0 stub: it never throws, resolves no known
 * {@code ProjectDefinition}, and is silently discarded on the player's next login drain. A 1.0.0 world
 * with banked entries opened in 0.9.1 therefore never crashes or corrupts — it just drops the banked
 * reward (expected: going backward across a save-format extension is inherently lossy for the new data).
 *
 * <p><b>{@code attempts}</b> counts delivery passes that failed without paying anything, so a reward
 * that can never be delivered stops being retried instead of throwing every login. It is written only
 * when non-zero, which keeps an untouched entry byte-identical to every earlier save shape, and it is
 * deliberately excluded from {@link #isSameReward} — two entries that differ only in how often we have
 * tried them are the same debt.
 */
public record PendingReward(Kind kind, @Nullable ResourceLocation projectId, int phase, int rewardIndex,
                             @Nullable BankedReward banked, @Nullable String instanceKey,
                             @Nullable CompoundTag instanceSnapshot, int attempts) {

    /** Retains the original public constructor and the legacy unscoped save shape. */
    public PendingReward(Kind kind, @Nullable ResourceLocation projectId, int phase, int rewardIndex,
                         @Nullable BankedReward banked) {
        this(kind, projectId, phase, rewardIndex, banked, null, null, 0);
    }

    /** Retains the instance-scoped constructor from before {@code attempts} existed. */
    public PendingReward(Kind kind, @Nullable ResourceLocation projectId, int phase, int rewardIndex,
                         @Nullable BankedReward banked, @Nullable String instanceKey,
                         @Nullable CompoundTag instanceSnapshot) {
        this(kind, projectId, phase, rewardIndex, banked, instanceKey, instanceSnapshot, 0);
    }

    /** The same owed reward, with a different count of failed delivery passes. */
    public PendingReward withAttempts(int attempts) {
        return new PendingReward(kind, projectId, phase, rewardIndex, banked, instanceKey,
                instanceSnapshot, attempts);
    }

    /**
     * Identity that ignores {@link #attempts()}. The generated record {@code equals} counts it, so
     * anything matching one owed reward against another — de-duplication, "is this entry still in the
     * list" — has to come through here or a retried entry looks like a different debt.
     */
    public boolean isSameReward(PendingReward other) {
        return withAttempts(0).equals(other.withAttempts(0));
    }

    public enum Kind {
        PROJECT_PHASE, BANKED
    }

    public static PendingReward ofPhase(ResourceLocation projectId, int phase, int rewardIndex) {
        return new PendingReward(Kind.PROJECT_PHASE, projectId, phase, rewardIndex, null);
    }

    public static PendingReward ofPhase(ProjectState state, int phase, int rewardIndex) {
        return new PendingReward(Kind.PROJECT_PHASE, state.projectId(), phase, rewardIndex,
                null, state.key().asString(), state.save());
    }

    /** Legacy entries can only be attributed to instances this player actually helped. */
    public boolean matchesInstance(ProjectState state, java.util.UUID player) {
        return kind == Kind.PROJECT_PHASE && state.projectId().equals(projectId)
                && (instanceKey != null ? instanceKey.equals(state.key().asString())
                : state.participants().contains(player) && state.isPhaseDistributed(phase));
    }

    public static PendingReward ofBanked(BankedReward banked) {
        return new PendingReward(Kind.BANKED, null, -1, -1, banked);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (attempts > 0) {
            tag.putInt("attempts", attempts); // absent when zero: legacy entries stay byte-identical
        }
        if (kind == Kind.BANKED) {
            tag.putString("kind", "banked");
            tag.put("banked", banked.save());
            return tag;
        }
        // Absent "kind" = legacy PROJECT_PHASE shape, byte-identical to pre-1.0.0 saves.
        tag.putString("project", projectId.toString());
        tag.putInt("phase", phase);
        tag.putInt("reward", rewardIndex);
        if (instanceKey != null) {
            tag.putString("instance", instanceKey);
        }
        if (instanceSnapshot != null) {
            tag.put("instance_state", instanceSnapshot.copy());
        }
        return tag;
    }

    /**
     * Empty means "skip this entry": either a malformed legacy tag (no "project" key) or an unrecognised
     * {@code kind} (forward-compat). Callers must skip-not-throw per entry so one bad sibling in a list
     * never takes the rest down with it.
     */
    public static Optional<PendingReward> load(CompoundTag tag) {
        int attempts = tag.getInt("attempts"); // NBT default 0: never tried, or written before 1.6.3
        if (!tag.contains("kind")) {
            if (!tag.contains("project")) {
                return Optional.empty();
            }
            if (tag.getInt("phase") < 0 || tag.getInt("reward") < 0) {
                return Optional.empty();
            }
            return Optional.of(new PendingReward(Kind.PROJECT_PHASE,
                    new ResourceLocation(tag.getString("project")), tag.getInt("phase"),
                    tag.getInt("reward"), null,
                    tag.contains("instance") ? tag.getString("instance") : null,
                    tag.contains("instance_state", net.minecraft.nbt.Tag.TAG_COMPOUND)
                            ? tag.getCompound("instance_state").copy() : null, attempts));
        }
        if ("banked".equals(tag.getString("kind"))) {
            return BankedReward.load(tag.getCompound("banked")).map(PendingReward::ofBanked)
                    .map(reward -> reward.withAttempts(attempts));
        }
        return Optional.empty(); // unknown kind (future version) - skip, never corrupt
    }
}
