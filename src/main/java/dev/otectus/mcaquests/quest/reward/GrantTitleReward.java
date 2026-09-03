package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.title.TitleScope;
import dev.otectus.mcaquests.quest.title.TitleService;
import dev.otectus.mcaquests.quest.title.Titles;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Awards a player title (spec 0.7.0). {@code scope} defaults to {@code village}, which attaches the title
 * to the giver's village; {@code global} grants it everywhere. Unlike {@code village_reputation}, this
 * reward does real work in {@link #grant} because it only needs the player and giver entity.
 */
public record GrantTitleReward(ResourceLocation title, TitleScope scope) implements QuestReward {

    public static final MapCodec<GrantTitleReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("title").forGetter(GrantTitleReward::title),
            TitleScope.CODEC.optionalFieldOf("scope", TitleScope.VILLAGE).forGetter(GrantTitleReward::scope)
    ).apply(instance, GrantTitleReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.GRANT_TITLE;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.grant_title", Titles.displayName(title));
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        TitleService.grant(player, scope, title, villager);
    }

    /** A village-scoped title falls back to the village the quest froze when the giver is not loaded. */
    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager, RewardContext context) {
        TitleService.grant(player, scope, title, villager, context.community());
    }
}
