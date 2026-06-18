package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/** Fish up a number of matching items (spec section 14). */
public record FishItemObjective(ItemTarget target, int count) implements QuestObjective {

    public static final Codec<FishItemObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemTarget.MAP_CODEC.forGetter(FishItemObjective::target),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(FishItemObjective::count)
    ).apply(instance, FishItemObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.FISH_ITEM;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.fish_item", count, target.describe());
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), count);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    public boolean matches(ItemStack caught) {
        return target.matches(caught);
    }
}
