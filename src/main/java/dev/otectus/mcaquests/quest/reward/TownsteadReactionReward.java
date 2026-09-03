package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.data.StrictCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Plays an extra Townstead reaction on completion (Townstead spec §5.5).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_reaction",
 *   "target": "giver",
 *   "task": "mcaquests:dockside_catch",
 *   "phase": "completed"
 * }
 * }</pre>
 *
 * <p>This is for a deliberate flourish. The ordinary lifecycle reactions -- accepted, ready,
 * completed, failed, abandoned -- fire automatically for every quest and need no reward entry; adding
 * one here is how a pack asks for something extra on top.
 *
 * <p>Purely cosmetic, and treated as such: a reaction that does not play is not reported as a reward
 * failure, because nothing a player earned went missing.
 */
public record TownsteadReactionReward(TownsteadTarget target, ResourceLocation task,
                                      String phase) implements TownsteadReward {

    public static final MapCodec<TownsteadReactionReward> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadReactionReward::target),
                    ResourceLocation.CODEC.fieldOf("task").forGetter(TownsteadReactionReward::task),
                    StrictCodecs.strictOptional(Codec.STRING, "phase", "completed")
                            .forGetter(TownsteadReactionReward::phase)
            ).apply(instance, TownsteadReactionReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.TOWNSTEAD_REACTION;
    }

    @Override
    public TownsteadCapability capability() {
        return TownsteadCapability.DISPATCH_REACTION;
    }

    @Override
    public boolean enabledByConfig() {
        return McaQuestsConfig.COMMON.townsteadReactionsEnabled.get();
    }

    /** Cosmetic, so it never blocks a turn-in however the server has configured reward failures. */
    @Override
    public boolean canApply(ServerPlayer player, @Nullable Entity villager) {
        return true;
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (!enabledByConfig() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity subject = resolveTarget(player, villager).orElse(null);
        if (subject instanceof LivingEntity living) {
            TownsteadBridge.Holder.get().dispatchTransition(level, living, task, phase);
        }
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.townstead_reaction");
    }
}
