package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.RewardTypes;

/**
 * A phase reward wrapped with a {@link SharedRewardTarget} that decides who receives it. Reuses the
 * existing {@link QuestReward} types verbatim:
 *
 * <pre>{ "reward": { "type": "mcaquests:xp", "amount": 30 }, "target": "contributors" }</pre>
 */
public record SharedReward(QuestReward reward, SharedRewardTarget target) {

    public static final Codec<SharedReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RewardTypes.CODEC.fieldOf("reward").forGetter(SharedReward::reward),
            SharedRewardTarget.CODEC.lenientOptionalFieldOf("target", SharedRewardTarget.CONTRIBUTORS).forGetter(SharedReward::target)
    ).apply(instance, SharedReward::new));
}
