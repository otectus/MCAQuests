package dev.otectus.mcaquests.quest.delivery;

import dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective;
import dev.otectus.mcaquests.quest.objective.DeliveryDestination;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a pre-1.6.5 save means once deliveries are counted in units.
 *
 * <p>The dangerous reading is the obvious one: the old villager delivery stored its hand-off as a
 * count of 1, and treating that as "one item delivered" would show a finished two-crossbow delivery as
 * 1/2 and ask the player to buy the same delivery twice. The opposite mistake is just as bad — minting
 * units for a quest that was never handed over, because the player happens to be carrying the goods.
 */
class DeliveryLedgerMigrationTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static DeliverToVillagerObjective villagerDelivery(Item item, int count, boolean consume) {
        return new DeliverToVillagerObjective(VillagerTarget.SELF,
                new ItemTarget(Optional.of(item), Optional.empty()), count, consume);
    }

    private static DeliverToVillagerObjective transferDelivery(Item item, int count) {
        return new DeliverToVillagerObjective(VillagerTarget.SELF,
                new ItemTarget(Optional.of(item), Optional.empty()), count, true,
                Optional.of(new DeliveryDestination(DeliveryDestination.Kind.TOWNSTEAD_VILLAGER_INVENTORY,
                        dev.otectus.mcaquests.compat.TownsteadTarget.RECIPIENT)));
    }

    @Test
    @DisplayName("a legacy completed villager delivery migrates as the whole payload, never as 1/N")
    void legacyCountMigratesToFullPayload() {
        DeliverToVillagerObjective objective = villagerDelivery(Items.CROSSBOW, 2, true);
        ObjectiveProgress progress = new ObjectiveProgress(1); // the pre-1.6.5 "handed over" marker

        assertEquals(2, DeliveryLedger.units(objective, progress));
        assertEquals(0, DeliveryLedger.outstanding(objective, progress));
    }

    @Test
    @DisplayName("migration is idempotent and survives a save/load round trip")
    void migrationIsIdempotent() {
        DeliverToVillagerObjective objective = villagerDelivery(Items.BLAZE_ROD, 6, true);
        ObjectiveProgress progress = new ObjectiveProgress(1);

        assertEquals(6, DeliveryLedger.units(objective, progress));
        assertEquals(6, DeliveryLedger.units(objective, progress), "a second read must not re-credit");

        ObjectiveProgress reloaded = ObjectiveProgress.load(progress.save());
        assertEquals(6, DeliveryLedger.units(objective, reloaded));
        assertEquals(6, DeliveryLedger.rawUnits(reloaded));
    }

    @Test
    @DisplayName("a legacy committed inventory transfer is preserved as fully delivered")
    void legacyTransferMarkerMigrates() {
        DeliverToVillagerObjective objective = transferDelivery(Items.BREAD, 8);
        ObjectiveProgress progress = new ObjectiveProgress();
        // The unusual saved ordering the old code could leave behind: marker written, count not yet set.
        progress.extra().putBoolean("delivered_to_inventory", true);

        assertEquals(8, DeliveryLedger.units(objective, progress));
    }

    @Test
    @DisplayName("a legacy item_delivery transfer marker is preserved and never charged again")
    void legacyItemDeliveryTransferMigrates() {
        ItemDeliveryObjective objective = new ItemDeliveryObjective(Items.WHEAT, 24, true,
                new DeliveryDestination(DeliveryDestination.Kind.TOWNSTEAD_VILLAGER_INVENTORY,
                        dev.otectus.mcaquests.compat.TownsteadTarget.GIVER));
        ObjectiveProgress progress = new ObjectiveProgress();
        progress.extra().putBoolean("delivered", true);

        assertEquals(24, objective.deliveredUnits(progress));
        assertEquals(0, objective.outstandingUnits(progress));
    }

    @Test
    @DisplayName("an incomplete legacy delivery has zero deposits and writes nothing")
    void incompleteLegacyStaysZero() {
        DeliverToVillagerObjective objective = villagerDelivery(Items.CROSSBOW, 2, true);
        ObjectiveProgress progress = new ObjectiveProgress();

        assertEquals(0, DeliveryLedger.units(objective, progress));
        assertFalse(DeliveryLedger.present(progress));
        assertNull(DeliveryLedger.peek(progress), "an untouched save must stay untouched");
    }

    @Test
    @DisplayName("carrying the goods is not a historical deposit")
    void possessionIsNotADeposit() {
        ItemDeliveryObjective objective = new ItemDeliveryObjective(Items.BLAZE_ROD, 6, true);
        ObjectiveProgress progress = new ObjectiveProgress();

        assertEquals(0, objective.deliveredUnits(progress));
        assertEquals(6, objective.outstandingUnits(progress));
    }

    @Test
    @DisplayName("a legacy non-consuming proof objective keeps proof semantics and banks no units")
    void legacyProofKeepsProofSemantics() {
        DeliverToVillagerObjective proof = villagerDelivery(Items.CROSSBOW, 2, false);
        ObjectiveProgress progress = new ObjectiveProgress(1);

        assertTrue(DeliveryLedger.isProofOnly(proof));
        assertEquals(0, DeliveryLedger.units(proof, progress), "nothing was ever taken");
        assertTrue(DeliveryLedger.proofAcknowledged(progress));
    }

    @Test
    @DisplayName("deposits are clamped to the requirement and cannot be banked past it")
    void creditClampsToRequirement() {
        DeliverToVillagerObjective objective = villagerDelivery(Items.CROSSBOW, 2, true);
        ObjectiveProgress progress = new ObjectiveProgress();
        String fingerprint = DeliveryLedger.fingerprintOf(objective);

        assertEquals(1, DeliveryLedger.credit(progress, fingerprint, 1, 2));
        assertEquals(2, DeliveryLedger.credit(progress, fingerprint, 5, 2));
        assertEquals(2, DeliveryLedger.units(objective, progress));
    }

    @Test
    @DisplayName("a malformed stored count is clamped rather than trusted")
    void malformedCountIsClamped() {
        DeliverToVillagerObjective objective = villagerDelivery(Items.CROSSBOW, 2, true);
        ObjectiveProgress progress = new ObjectiveProgress();
        CompoundTag ledger = new CompoundTag();
        ledger.putInt("schema", 1);
        ledger.putInt("delivered_units", -9999);
        ledger.putString("definition_fingerprint", DeliveryLedger.fingerprintOf(objective));
        progress.extra().put(DeliveryLedger.KEY, ledger);

        assertEquals(0, DeliveryLedger.units(objective, progress));
    }

    @Test
    @DisplayName("unknown keys in the ledger survive a credit written by this version")
    void unknownLedgerKeysArePreserved() {
        DeliverToVillagerObjective objective = villagerDelivery(Items.CROSSBOW, 2, true);
        ObjectiveProgress progress = new ObjectiveProgress();
        CompoundTag ledger = new CompoundTag();
        ledger.putInt("schema", 1);
        ledger.putInt("delivered_units", 1);
        ledger.putString("definition_fingerprint", DeliveryLedger.fingerprintOf(objective));
        ledger.putString("some_future_field", "keep me");
        progress.extra().put(DeliveryLedger.KEY, ledger);

        DeliveryLedger.credit(progress, DeliveryLedger.fingerprintOf(objective), 1, 2);

        CompoundTag stored = ObjectiveProgress.load(progress.save()).extra().getCompound(DeliveryLedger.KEY);
        assertEquals(2, stored.getInt("delivered_units"));
        assertEquals("keep me", stored.getString("some_future_field"));
    }

    @Test
    @DisplayName("deposits made against another item block rather than pay a reshaped objective")
    void changedDefinitionBlocksInsteadOfReassigning() {
        DeliverToVillagerObjective crossbows = villagerDelivery(Items.CROSSBOW, 2, true);
        ObjectiveProgress progress = new ObjectiveProgress();
        DeliveryLedger.credit(progress, DeliveryLedger.fingerprintOf(crossbows), 2, 2);

        DeliverToVillagerObjective wheat = villagerDelivery(Items.WHEAT, 2, true);
        assertTrue(DeliveryLedger.blocked(wheat, progress));
        assertEquals(0, DeliveryLedger.units(wheat, progress), "credit must not be handed to another item");
        assertEquals(2, DeliveryLedger.rawUnits(progress), "but the record itself is preserved");
    }

    @Test
    @DisplayName("retuning the quantity is not a conflict: the deposits still mean the same goods")
    void quantityChangeRemapsCleanly() {
        DeliverToVillagerObjective four = villagerDelivery(Items.BLAZE_ROD, 4, true);
        ObjectiveProgress progress = new ObjectiveProgress();
        DeliveryLedger.credit(progress, DeliveryLedger.fingerprintOf(four), 4, 4);

        DeliverToVillagerObjective six = villagerDelivery(Items.BLAZE_ROD, 6, true);
        assertFalse(DeliveryLedger.blocked(six, progress));
        assertEquals(4, DeliveryLedger.units(six, progress));
        assertEquals(2, DeliveryLedger.outstanding(six, progress));

        DeliverToVillagerObjective two = villagerDelivery(Items.BLAZE_ROD, 2, true);
        assertEquals(2, DeliveryLedger.units(two, progress), "a shrunken requirement clamps, not overflows");
    }
}
