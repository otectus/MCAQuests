package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * "Donate N of an item into the project pool." Unlike the quest {@code item_delivery} (possession,
 * consumed only at final turn-in), a donation is consumed <em>immediately</em> at the sponsor click and
 * banked into shared progress — so a shared pool can never double-count two players each merely holding
 * the items.
 */
public record DonateItemObjective(ItemTarget target, int count, int perPlayerCap) implements ProjectObjective {

    public static final MapCodec<DonateItemObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemTarget.MAP_CODEC.forGetter(DonateItemObjective::target),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(DonateItemObjective::count),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("per_player_cap", 0).forGetter(DonateItemObjective::perPlayerCap)
    ).apply(instance, DonateItemObjective::new));

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.DONATE_ITEM;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.donate_item", count, target.describe());
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public boolean isEventDriven() {
        return false;
    }

    @Override
    public boolean isContribution() {
        return true;
    }

    @Override
    public int perPlayerCap() {
        return perPlayerCap;
    }

    @Override
    public int contribute(ServerPlayer player, SharedObjectiveProgress progress, int effectiveCap) {
        int remaining = count - progress.count();
        if (remaining <= 0) {
            return 0;
        }
        if (effectiveCap > 0) {
            remaining = Math.min(remaining, effectiveCap - progress.contributionOf(player.getUUID()));
        }
        if (remaining <= 0) {
            return 0;
        }
        int take = Math.min(remaining, countInInventory(player));
        if (take <= 0) {
            return 0;
        }
        consume(player, take);
        progress.add(take);
        progress.addContribution(player.getUUID(), take);
        return take;
    }

    private int countInInventory(ServerPlayer player) {
        int found = 0;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (target.matches(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private void consume(ServerPlayer player, int amount) {
        int remaining = amount;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (target.matches(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }
}
