package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.FtbqBridge;
import dev.otectus.mcaquests.compat.FtbqIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Grants FTB Quests progress for the player's team (spec section 19). Uses {@link FtbqBridge} to apply
 * one of three actions: {@code complete_task}, {@code complete_quest}, or {@code reset_task}.
 *
 * <p>The reward is fire-and-forget: {@link #grant} delegates to the bridge with no validation, and logs
 * at DEBUG level if the bridge is unavailable, the id is unknown, or the config disables FTB progress
 * grants ({@code allowFtbqProgressRewards}). The {@link #describe()} message still renders even when
 * disabled, mirroring the {@code command} reward's behavior (spec §19).
 */
public record FtbqProgressReward(ProgressAction action, String id) implements QuestReward {

    private static final String TYPE_ID = "mcaquests:ftbq_progress";

    /**
     * Action enum with lowercase JSON serialization matching spec §19 ({@code complete_task},
     * {@code complete_quest}, {@code reset_task}). Maps to {@link FtbqBridge.ProgressAction}.
     */
    public enum ProgressAction {
        COMPLETE_TASK, COMPLETE_QUEST, RESET_TASK;

        public static final Codec<ProgressAction> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return com.mojang.serialization.DataResult.success(
                                valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return com.mojang.serialization.DataResult.error(() ->
                                "Unknown ftbq_progress action: '" + name
                                        + "' (expected complete_task/complete_quest/reset_task)");
                    }
                },
                value -> com.mojang.serialization.DataResult.success(
                        value.name().toLowerCase(Locale.ROOT)));

        public FtbqBridge.ProgressAction toBridgeAction() {
            return switch (this) {
                case COMPLETE_TASK -> FtbqBridge.ProgressAction.COMPLETE_TASK;
                case COMPLETE_QUEST -> FtbqBridge.ProgressAction.COMPLETE_QUEST;
                case RESET_TASK -> FtbqBridge.ProgressAction.RESET_TASK;
            };
        }
    }

    public static final MapCodec<FtbqProgressReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ProgressAction.CODEC.fieldOf("action").forGetter(FtbqProgressReward::action),
            FtbqIds.hexIdCodec(TYPE_ID, "id").fieldOf("id").forGetter(FtbqProgressReward::id)
    ).apply(instance, FtbqProgressReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.FTBQ_PROGRESS;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.ftbq_progress.describe." + action.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (!McaQuestsConfig.COMMON.allowFtbqProgressRewards.get()) {
            McaQuests.LOGGER.debug("[MCA: Quests] Skipped ftbq_progress reward (allowFtbqProgressRewards is false): action={}, id={}",
                    action.name().toLowerCase(Locale.ROOT), id);
            return;
        }

        try {
            FtbqBridge bridge = FtbqBridge.Holder.get();
            bridge.grantProgress(player, action.toBridgeAction(), id);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] ftbq_progress reward grant failed: action={}, id={}",
                    action.name().toLowerCase(Locale.ROOT), id, t);
        }
    }
}
