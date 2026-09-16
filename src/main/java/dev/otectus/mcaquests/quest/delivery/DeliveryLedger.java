package dev.otectus.mcaquests.quest.delivery;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective;
import dev.otectus.mcaquests.quest.objective.DeliveryDestination;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * What a player has actually handed over, stored with the objective's own progress.
 *
 * <p>The ledger answers one question — <b>how many units of this obligation are already paid</b> — and
 * it is the only thing allowed to answer it. Possession is not a deposit: carrying two crossbows is not
 * the same fact as having given two crossbows away, and conflating those two was why a player with
 * everything the quest asked for could still be told the delivery was incomplete.
 *
 * <pre>{@code
 * extra.mcaquests_delivery = {
 *   schema: 1,
 *   delivered_units: 1,
 *   proof_acknowledged: false,
 *   definition_fingerprint: "deliver_to_villager:minecraft:crossbow"
 * }
 * }</pre>
 *
 * <p>Counts only, never {@link net.minecraft.world.item.ItemStack}s. A ledger of stacks would need a
 * registry lookup provider to load, would drift from the goods actually in a villager's inventory, and
 * would be a second place a duplication bug could live. What was transferred is already recorded by the
 * transfer itself; this records only that it happened.
 *
 * <p>The compound is written <em>into</em> whatever is already under its key, so a field a later
 * version adds survives a round trip through an older one, and no other objective state is touched.
 */
public final class DeliveryLedger {

    private DeliveryLedger() {
    }

    /** The key inside {@link ObjectiveProgress#extra()}. Namespaced: {@code extra} is shared scratch. */
    public static final String KEY = "mcaquests_delivery";

    public static final int SCHEMA = 1;

    private static final String K_SCHEMA = "schema";
    private static final String K_UNITS = "delivered_units";
    private static final String K_PROOF = "proof_acknowledged";
    private static final String K_FINGERPRINT = "definition_fingerprint";

    /**
     * The pre-1.6.5 marker {@code deliver_to_villager} wrote once a transfer into the recipient's own
     * inventory had committed. Read for migration; never written again.
     */
    private static final String LEGACY_VILLAGER_TRANSFER = "delivered_to_inventory";

    /** The pre-1.6.5 marker {@code item_delivery} wrote once its transfer had committed. */
    private static final String LEGACY_ITEM_TRANSFER = "delivered";

    // ---------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------

    /** True when this objective already carries a ledger (so migration has nothing left to do). */
    public static boolean present(ObjectiveProgress progress) {
        return progress.extra().contains(KEY, Tag.TAG_COMPOUND)
                && progress.extra().getCompound(KEY).contains(K_SCHEMA);
    }

    /** Committed units as stored, clamped to a sane range. Zero when there is no ledger. */
    public static int rawUnits(ObjectiveProgress progress) {
        return clamp(tagOrEmpty(progress).getInt(K_UNITS));
    }

    /** True when a non-consuming proof objective has been shown its full quantity. */
    public static boolean proofAcknowledged(ObjectiveProgress progress) {
        return tagOrEmpty(progress).getBoolean(K_PROOF);
    }

    /** The definition identity these deposits were made against, if one was recorded. */
    public static Optional<String> fingerprint(ObjectiveProgress progress) {
        CompoundTag tag = tagOrEmpty(progress);
        return tag.contains(K_FINGERPRINT, Tag.TAG_STRING)
                ? Optional.of(tag.getString(K_FINGERPRINT))
                : Optional.empty();
    }

    /**
     * Committed units for {@code objective}, migrating a pre-1.6.5 save on the way if needed.
     *
     * <p>This is the canonical read: the objectives, the service, the completion check and the menu all
     * come through here, so there is exactly one interpretation of an old save in the build.
     *
     * <p>Returns zero for a definition whose identity no longer matches what the deposits were made
     * against (see {@link #blocked}). The record is kept, not erased — restoring the datapack restores
     * the credit — but a deposit of six blaze rods is not silently spent on a delivery of wheat.
     */
    public static int units(QuestObjective objective, ObjectiveProgress progress) {
        int required = requiredUnits(objective);
        if (required <= 0) {
            return 0;
        }
        String fingerprint = fingerprintOf(objective);
        if (isProofOnly(objective)) {
            // "Show me six rods" never takes anything, so it has no units to hold, however satisfied it
            // is. Migrating it as a deposit would be inventing a donation that never happened.
            migrateProof(progress, fingerprint, legacySatisfied(objective, progress));
            return 0;
        }
        if (!present(progress)) {
            return migrate(progress, fingerprint, required, legacySatisfied(objective, progress));
        }
        if (blocked(progress, fingerprint)) {
            return 0;
        }
        return Math.min(required, rawUnits(progress));
    }

    /** Outstanding units for {@code objective}: what is still owed after the ledger. */
    public static int outstanding(QuestObjective objective, ObjectiveProgress progress) {
        return Math.max(0, requiredUnits(objective) - units(objective, progress));
    }

    /**
     * True when the deposits on record were made against a different obligation than the one now at
     * this objective index, and cannot be remapped without inventing or destroying credit.
     *
     * <p>The fingerprint deliberately excludes the <em>quantity</em>: a pack that retunes "bring four"
     * to "bring six" has not changed what the player handed over, and the deposits still mean the same
     * goods. Changing the item, or the objective type, is a different obligation, and that is the case
     * this refuses to guess about.
     */
    public static boolean blocked(ObjectiveProgress progress, String fingerprint) {
        if (!present(progress)) {
            return false;
        }
        if (rawUnits(progress) <= 0 && !proofAcknowledged(progress)) {
            return false; // nothing at stake; the next deposit rewrites the fingerprint
        }
        Optional<String> recorded = fingerprint(progress);
        boolean mismatch = recorded.isPresent() && !recorded.get().equals(fingerprint);
        if (mismatch && REPORTED_CONFLICTS.add(recorded.get() + " -> " + fingerprint)) {
            // Once per pair, not once per read: this question is asked by the completion check, which
            // runs on the per-second progress tick, and an admin needs the line once to act on it.
            McaQuests.LOGGER.warn("[MCA: Quests] delivery deposits recorded against '{}' cannot be applied to "
                    + "'{}'; the record is kept and the delivery is blocked rather than reset",
                    recorded.get(), fingerprint);
        }
        return mismatch;
    }

    /** Conflicting fingerprint pairs already logged, so the per-second completion check says it once. */
    private static final java.util.Set<String> REPORTED_CONFLICTS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** As {@link #blocked(ObjectiveProgress, String)}, for the objective now at this index. */
    public static boolean blocked(QuestObjective objective, ObjectiveProgress progress) {
        return blocked(progress, fingerprintOf(objective));
    }

    // ---------------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------------

    /**
     * Records {@code units} more as committed and returns the new total.
     *
     * <p>Called only <em>after</em> the inventory transaction has committed, so what is written is what
     * actually moved. Clamped to the requirement: surplus is never banked, because the extra goods were
     * never taken.
     */
    public static int credit(ObjectiveProgress progress, String fingerprint, int units, int requiredUnits) {
        CompoundTag tag = mutableTag(progress);
        int total = Math.max(0, Math.min(requiredUnits, clamp(tag.getInt(K_UNITS)) + Math.max(0, units)));
        tag.putInt(K_SCHEMA, SCHEMA);
        tag.putInt(K_UNITS, total);
        tag.putString(K_FINGERPRINT, fingerprint);
        progress.extra().put(KEY, tag);
        return total;
    }

    /** Records that a non-consuming proof objective has been shown its full quantity. */
    public static void acknowledgeProof(ObjectiveProgress progress, String fingerprint) {
        CompoundTag tag = mutableTag(progress);
        tag.putInt(K_SCHEMA, SCHEMA);
        tag.putBoolean(K_PROOF, true);
        tag.putString(K_FINGERPRINT, fingerprint);
        progress.extra().put(KEY, tag);
    }

    // ---------------------------------------------------------------------------------------------
    // Migration (spec section 4)
    // ---------------------------------------------------------------------------------------------

    /**
     * Interprets a pre-1.6.5 save once, idempotently.
     *
     * <p>A legacy {@code deliver_to_villager} recorded its hand-off as a single boolean-ish count, so
     * {@code count >= 1} means <b>the whole payload was handed over</b> — two crossbows, six rods — and
     * migrating it as one unit would take a finished delivery and show it as 1/2, asking the player to
     * pay again for goods that are already gone. An incomplete legacy delivery migrates to zero:
     * carrying the items has never been the same thing as having delivered them.
     *
     * <p>Nothing is written for a quest with no legacy credit. An untouched save stays untouched, which
     * is what makes this safe to run on every read.
     */
    public static int migrate(ObjectiveProgress progress, String fingerprint, int requiredUnits,
                              boolean legacySatisfied) {
        if (present(progress)) {
            return Math.min(requiredUnits, rawUnits(progress));
        }
        if (!legacySatisfied || requiredUnits <= 0) {
            return 0;
        }
        CompoundTag tag = mutableTag(progress);
        tag.putInt(K_SCHEMA, SCHEMA);
        tag.putInt(K_UNITS, requiredUnits);
        tag.putString(K_FINGERPRINT, fingerprint);
        progress.extra().put(KEY, tag);
        return requiredUnits;
    }

    /**
     * Migrates a legacy non-consuming proof objective, which had no deposit to preserve — only the fact
     * that the player had once shown the full quantity. Preserving that as units would mint credit for
     * goods that were never taken.
     */
    public static boolean migrateProof(ObjectiveProgress progress, String fingerprint, boolean legacySatisfied) {
        if (present(progress)) {
            return proofAcknowledged(progress);
        }
        if (!legacySatisfied) {
            return false;
        }
        acknowledgeProof(progress, fingerprint);
        return true;
    }

    /** True when a pre-1.6.5 save recorded this objective as delivered. */
    public static boolean legacySatisfied(QuestObjective objective, ObjectiveProgress progress) {
        if (objective instanceof DeliverToVillagerObjective) {
            return progress.count() >= 1 || progress.extra().getBoolean(LEGACY_VILLAGER_TRANSFER);
        }
        if (objective instanceof ItemDeliveryObjective) {
            // Possession was never a deposit, so only a committed transfer counts here.
            return progress.extra().getBoolean(LEGACY_ITEM_TRANSFER);
        }
        return false;
    }

    /** Writes the pre-1.6.5 markers a fully-paid obligation used to carry, for anything still reading them. */
    public static void mirrorLegacyMarkers(QuestObjective objective, ObjectiveProgress progress) {
        if (objective instanceof DeliverToVillagerObjective deliver) {
            if (deliver.destination().map(DeliveryDestination::isTransfer).orElse(false)) {
                progress.extra().putBoolean(LEGACY_VILLAGER_TRANSFER, true);
            }
            if (progress.count() < 1) {
                progress.setCount(1);
            }
        } else if (objective instanceof ItemDeliveryObjective delivery && delivery.destination().isTransfer()) {
            progress.extra().putBoolean(LEGACY_ITEM_TRANSFER, true);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------------------------------

    /**
     * A stable identity for the obligation a deposit was made against, so a datapack reload that
     * reorders or reshapes objectives cannot hand old deposits to a different one.
     *
     * <p>Type and item only. See {@link #blocked} for why the quantity is deliberately absent.
     */
    public static String fingerprintOf(QuestObjective objective) {
        if (objective instanceof DeliverToVillagerObjective deliver) {
            return "deliver_to_villager:" + itemKey(deliver.item());
        }
        if (objective instanceof ItemDeliveryObjective delivery) {
            return "item_delivery:" + BuiltInRegistries.ITEM.getKey(delivery.item());
        }
        return objective.type().id().toString();
    }

    /**
     * True for an objective that asks to be <em>shown</em> goods rather than given them:
     * {@code consume: false} with no transfer destination (spec section 5).
     *
     * <p>These keep their old meaning exactly — a full-quantity check against what the player is
     * carrying — because a player who shows one crossbow six times has donated nothing, and letting the
     * deposit formula near them would bank six units for one item.
     */
    public static boolean isProofOnly(QuestObjective objective) {
        if (objective instanceof DeliverToVillagerObjective deliver) {
            return !deliver.consume() && !deliver.destination().map(DeliveryDestination::isTransfer).orElse(false);
        }
        if (objective instanceof ItemDeliveryObjective delivery) {
            return !delivery.consume() && !delivery.destination().isTransfer();
        }
        return false;
    }

    /** The units this objective asks for, or 0 when it is not a delivery. */
    public static int requiredUnits(QuestObjective objective) {
        if (objective instanceof DeliverToVillagerObjective deliver) {
            return deliver.itemCount();
        }
        if (objective instanceof ItemDeliveryObjective delivery) {
            return delivery.count();
        }
        return 0;
    }

    private static String itemKey(ItemTarget target) {
        return target.item().map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                .or(() -> target.tag().map(tag -> "#" + tag.location()))
                .orElse("?");
    }

    // ---------------------------------------------------------------------------------------------

    private static CompoundTag tagOrEmpty(ObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        return extra.contains(KEY, Tag.TAG_COMPOUND) ? extra.getCompound(KEY) : new CompoundTag();
    }

    /**
     * The stored compound itself when there is one, so unknown keys written by another version survive
     * every update made here; a fresh one otherwise.
     */
    private static CompoundTag mutableTag(ObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        return extra.contains(KEY, Tag.TAG_COMPOUND) ? extra.getCompound(KEY) : new CompoundTag();
    }

    /** Malformed counts are clamped rather than trusted: this number is allowed to spend a player's items. */
    private static int clamp(int stored) {
        return Math.max(0, Math.min(stored, MAX_UNITS));
    }

    /**
     * An upper bound far above any sane requirement but well below overflow, so a corrupted or
     * hand-edited count cannot become an unbounded loop or a negative remainder.
     */
    public static final int MAX_UNITS = 1 << 20;

    /** Package-visible for the migration tests, which must be able to build a pre-1.6.5 save shape. */
    @Nullable
    static CompoundTag peek(ObjectiveProgress progress) {
        return progress.extra().contains(KEY, Tag.TAG_COMPOUND) ? progress.extra().getCompound(KEY) : null;
    }
}
