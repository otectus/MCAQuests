package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.capitals.CapitalRef;
import dev.otectus.mcaquests.compat.capitals.CapitalsBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.capitals.CapitalsQueries;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Raises a villager to a noble rank in the capital the quest was accepted in (MCA Capitals).
 *
 * <pre>{@code
 * {"type": "mcaquests:capital_villager_title", "villager": {"mode": "self"}, "title": "lord"}
 * }</pre>
 *
 * <p>The quest giver by default — the villager who asked the player to buy them a lordship is the one
 * who gets it. Only {@code knight}, {@code lord} and {@code duke} exist on the villager side of
 * Capitals; an archduchy is a player rank and has no villager seat to fill.
 *
 * <p>The rank must be spelled for the villager's gender, which MCA knows and Capitals does not ask
 * about, so it is read before the grant and the masculine form is used when MCA cannot answer.
 */
public record CapitalVillagerTitleReward(VillagerTarget villager, String title) implements QuestReward {

    /** The villager-seatable ranks, masculine constant to feminine constant, in Capitals' spelling. */
    private static final Map<String, String> FEMININE = Map.of(
            "KNIGHT", "DAME",
            "LORD", "LADY",
            "DUKE", "DUCHESS");

    // mapCodec(...)...codec(), never create(...) chained: a Codec that is not a MapCodecCodec makes
    // DFU's dispatch look for the fields under a nested "value" key instead of inline beside "type".
    // See DispatchedCodecInlinesTest.
    public static final Codec<CapitalVillagerTitleReward> CODEC =
            RecordCodecBuilder.<CapitalVillagerTitleReward>mapCodec(instance -> instance.group(
                    StrictCodecs.strictOptional(VillagerTarget.CODEC, "villager", VillagerTarget.SELF)
                            .forGetter(CapitalVillagerTitleReward::villager),
                    Codec.STRING.fieldOf("title").forGetter(CapitalVillagerTitleReward::title)
            ).apply(instance, CapitalVillagerTitleReward::new))
                    .flatXmap(CapitalVillagerTitleReward::validate, CapitalVillagerTitleReward::validate)
                    .codec();

    private static DataResult<CapitalVillagerTitleReward> validate(CapitalVillagerTitleReward reward) {
        String value = reward.title.toLowerCase(Locale.ROOT);
        if (!FEMININE.containsKey(value.toUpperCase(Locale.ROOT))) {
            return DataResult.error(() -> "capital_villager_title: unknown title '" + reward.title
                    + "' (expected knight/lord/duke)");
        }
        return DataResult.success(value.equals(reward.title)
                ? reward
                : new CapitalVillagerTitleReward(reward.villager, value));
    }

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.CAPITAL_VILLAGER_TITLE;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.capital_villager_title",
                villager.describe(), titleLabel(title));
    }

    /** Needs the capital the quest was accepted in, which only the context carries. */
    @Override
    public void grant(ServerPlayer player, @Nullable Entity giver) {
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity giver, RewardContext context) {
        CapitalsBridge bridge = CapitalsCompat.bridge();
        if (!bridge.has(CapitalsCapability.VILLAGER_TITLES)) {
            McaQuests.LOGGER.warn("[MCA: Quests] Skipping a capital_villager_title reward on quest '{}': "
                    + "MCA Capitals cannot seat villagers right now.", context.questId());
            return;
        }
        MinecraftServer server = player.getServer();
        ServerLevel level = context.level(player);
        Optional<CapitalRef> capital = CapitalsQueries.capitalForContext(server, context);
        if (level == null || capital.isEmpty()) {
            McaQuests.LOGGER.warn("[MCA: Quests] Skipping a capital_villager_title reward on quest '{}': "
                    + "the village it was accepted in is no longer the seat of a capital.", context.questId());
            return;
        }
        Entity subject = giver != null ? giver : level.getEntity(context.giverUuid());
        Optional<LivingEntity> target =
                villager.resolveFrom(player, subject, level, context.questId());
        if (target.isEmpty()) {
            McaQuests.LOGGER.warn("[MCA: Quests] Skipping a capital_villager_title reward on quest '{}': "
                    + "its villager could not be resolved.", context.questId());
            return;
        }
        java.util.UUID subjectId = target.get().getUUID();
        bridge.setVillagerTitle(level, capital.get(), subjectId,
                nobleTitleConstant(bridge.isVillagerFemale(level, subjectId).orElse(false)));
    }

    /** The Capitals {@code NobleTitle} constant this reward seats a villager of that gender in. */
    public String nobleTitleConstant(boolean female) {
        String masculine = title.toUpperCase(Locale.ROOT);
        return female ? FEMININE.get(masculine) : masculine;
    }

    private static Component titleLabel(String constant) {
        return Component.translatable("mcaquests.capitals.title." + constant.toLowerCase(Locale.ROOT));
    }
}
