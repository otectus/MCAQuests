package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Runs a server command (spec section 15). <b>Disabled by default</b> ({@code allowCommandRewards});
 * if disabled, the reward is skipped with a warning. The command runs from the player's source at
 * permission level 2 with output suppressed; {@code @s} refers to the player.
 */
public record CommandReward(String command) implements QuestReward {

    public static final Codec<CommandReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("command").forGetter(CommandReward::command)
    ).apply(instance, CommandReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.COMMAND;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.command");
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (!McaQuestsConfig.COMMON.allowCommandRewards.get()) {
            McaQuests.LOGGER.warn("[MCA: Quests] Skipped command reward (allowCommandRewards is false): {}", command);
            return;
        }
        if (player.getServer() == null) {
            return;
        }
        CommandSourceStack source = player.createCommandSourceStack()
                .withPermission(2)
                .withSuppressedOutput();
        player.getServer().getCommands().performPrefixedCommand(source, command);
    }
}
