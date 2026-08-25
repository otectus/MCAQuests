package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.data.StrictCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Teaches a villager a Townstead skill, or takes one away (Townstead spec §5.5).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_skill",
 *   "target": "giver",
 *   "skill": "townstead:crop_rotation",
 *   "forget": false
 * }
 * }</pre>
 *
 * <p>Teaching a skill the villager already knows is a success that changes nothing, so a repeatable
 * quest can carry one without the second completion looking like a failure. {@code force} skips
 * Townstead prerequisite checks and, like an uncapped XP award, needs the server to allow it too.
 */
public record TownsteadSkillReward(TownsteadTarget target, ResourceLocation skill, boolean forget,
                                   boolean force) implements TownsteadReward {

    public static final Codec<TownsteadSkillReward> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadSkillReward::target),
                    ResourceLocation.CODEC.fieldOf("skill").forGetter(TownsteadSkillReward::skill),
                    StrictCodecs.strictOptional(Codec.BOOL, "forget", false)
                            .forGetter(TownsteadSkillReward::forget),
                    StrictCodecs.strictOptional(Codec.BOOL, "force", false)
                            .forGetter(TownsteadSkillReward::force)
            ).apply(instance, TownsteadSkillReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.TOWNSTEAD_SKILL;
    }

    @Override
    public TownsteadCapability capability() {
        return TownsteadCapability.MUTATE_SKILLS;
    }

    @Override
    public boolean enabledByConfig() {
        return McaQuestsConfig.COMMON.townsteadSkillRewardsEnabled.get();
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (!enabledByConfig()) {
            return;
        }
        Entity subject = resolveTarget(player, villager).orElse(null);
        if (subject == null) {
            return;
        }
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        TownsteadMutationResult result = forget
                ? bridge.forgetSkill(subject, skill)
                : bridge.learnSkill(subject, skill,
                        force && McaQuestsConfig.COMMON.townsteadAllowUncappedProfessionXp.get());
        if (!result.succeeded()) {
            reportFailure(result);
        }
    }

    @Override
    public Component describe() {
        return Component.translatable(forget
                ? "mcaquests.reward.townstead_skill.forget"
                : "mcaquests.reward.townstead_skill.learn", skill.toString());
    }
}
