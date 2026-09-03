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
 */
public record PendingReward(Kind kind, @Nullable ResourceLocation projectId, int phase, int rewardIndex,
                             @Nullable BankedReward banked) {

    public enum Kind {
        PROJECT_PHASE, BANKED
    }

    public static PendingReward ofPhase(ResourceLocation projectId, int phase, int rewardIndex) {
        return new PendingReward(Kind.PROJECT_PHASE, projectId, phase, rewardIndex, null);
    }

    public static PendingReward ofBanked(BankedReward banked) {
        return new PendingReward(Kind.BANKED, null, -1, -1, banked);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (kind == Kind.BANKED) {
            tag.putString("kind", "banked");
            tag.put("banked", banked.save());
            return tag;
        }
        // Absent "kind" = legacy PROJECT_PHASE shape, byte-identical to pre-1.0.0 saves.
        tag.putString("project", projectId.toString());
        tag.putInt("phase", phase);
        tag.putInt("reward", rewardIndex);
        return tag;
    }

    /**
     * Empty means "skip this entry": either a malformed legacy tag (no "project" key) or an unrecognised
     * {@code kind} (forward-compat). Callers must skip-not-throw per entry so one bad sibling in a list
     * never takes the rest down with it.
     */
    public static Optional<PendingReward> load(CompoundTag tag) {
        if (!tag.contains("kind")) {
            if (!tag.contains("project")) {
                return Optional.empty();
            }
            return Optional.of(ofPhase(ResourceLocation.parse(tag.getString("project")),
                    tag.getInt("phase"), tag.getInt("reward")));
        }
        if ("banked".equals(tag.getString("kind"))) {
            return BankedReward.load(tag.getCompound("banked")).map(PendingReward::ofBanked);
        }
        return Optional.empty(); // unknown kind (future version) - skip, never corrupt
    }
}
