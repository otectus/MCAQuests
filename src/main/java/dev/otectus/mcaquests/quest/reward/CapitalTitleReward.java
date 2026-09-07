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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Raises the player to a noble rank in the capital the quest was accepted in (MCA Capitals).
 *
 * <pre>{@code {"type": "mcaquests:capital_title", "title": "knight"}}</pre>
 *
 * <p>Only the four granted ranks are available: {@code knight}, {@code lord}, {@code duke},
 * {@code archduke}. Thrones and royal ranks are Capitals' own business — a quest reward may make a
 * player a knight of a realm, never its king.
 *
 * <p>Capitals spells each rank twice, once per gender, and picks the wrong one silently if asked
 * wrongly. The feminine constant is chosen from MCA's record of the player, and {@code female_title}
 * overrides it for a server that has renamed the rank.
 */
public record CapitalTitleReward(String title, Optional<String> femaleTitle) implements QuestReward {

    /** The grantable ranks, masculine constant to feminine constant, in Capitals' own spelling. */
    private static final Map<String, String> FEMININE = Map.of(
            "KNIGHT", "DAME",
            "LORD", "LADY",
            "DUKE", "DUCHESS",
            "ARCHDUKE", "ARCHDUCHESS");

    // mapCodec(...)...codec(), never create(...) chained: a Codec that is not a MapCodecCodec makes
    // DFU's dispatch look for the fields under a nested "value" key instead of inline beside "type".
    // See DispatchedCodecInlinesTest.
    public static final Codec<CapitalTitleReward> CODEC = RecordCodecBuilder.<CapitalTitleReward>mapCodec(
            instance -> instance.group(
                    Codec.STRING.fieldOf("title").forGetter(CapitalTitleReward::title),
                    StrictCodecs.strictOptional(Codec.STRING, "female_title")
                            .forGetter(CapitalTitleReward::femaleTitle)
            ).apply(instance, CapitalTitleReward::new))
            .flatXmap(CapitalTitleReward::validate, CapitalTitleReward::validate).codec();

    /**
     * Rejects a rank Capitals does not grant, at reload. Writing {@code "title": "queen"} is a mistake
     * that would otherwise be discovered by a player finishing a long quest and receiving nothing.
     */
    private static DataResult<CapitalTitleReward> validate(CapitalTitleReward reward) {
        String value = reward.title.toLowerCase(Locale.ROOT);
        if (!FEMININE.containsKey(value.toUpperCase(Locale.ROOT))) {
            return DataResult.error(() -> "capital_title: unknown title '" + reward.title
                    + "' (expected knight/lord/duke/archduke)");
        }
        if (reward.femaleTitle.isPresent()) {
            String override = reward.femaleTitle.get().toUpperCase(Locale.ROOT);
            if (!FEMININE.containsKey(override) && !FEMININE.containsValue(override)) {
                return DataResult.error(() -> "capital_title: unknown female_title '"
                        + reward.femaleTitle.get() + "' (expected a grantable noble title)");
            }
        }
        return DataResult.success(value.equals(reward.title)
                ? reward
                : new CapitalTitleReward(value, reward.femaleTitle));
    }

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.CAPITAL_TITLE;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.capital_title", titleLabel(title));
    }

    /**
     * Without the accept-time context there is no capital to be knighted in: the player's current
     * position is not the court that set the quest, and granting a rank in the wrong realm is worse
     * than granting none.
     */
    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager, RewardContext context) {
        CapitalsBridge bridge = CapitalsCompat.bridge();
        if (!bridge.has(CapitalsCapability.TITLE_GRANTS)) {
            McaQuests.LOGGER.warn("[MCA: Quests] Skipping a capital_title reward on quest '{}': MCA "
                    + "Capitals cannot grant player titles right now.", context.questId());
            return;
        }
        MinecraftServer server = player.getServer();
        ServerLevel level = context.level(player);
        Optional<CapitalRef> capital = CapitalsQueries.capitalForContext(server, context);
        if (level == null || capital.isEmpty()) {
            McaQuests.LOGGER.warn("[MCA: Quests] Skipping a capital_title reward on quest '{}': the "
                    + "village it was accepted in is no longer the seat of a capital.", context.questId());
            return;
        }
        String constant = constantFor(bridge, level, player);
        if (!bridge.grantPlayerTitle(level, capital.get(), player.getUUID(), constant)) {
            return;
        }
        player.sendSystemMessage(Component.translatable("mcaquests.reward.capital_title.granted",
                titleLabel(constant.toLowerCase(Locale.ROOT)),
                Component.literal(CapitalsQueries.capitalName(level, capital.get()).orElse(""))));
    }

    /** The Capitals constant to grant: feminine when MCA says so, masculine when it says nothing. */
    private String constantFor(CapitalsBridge bridge, ServerLevel level, ServerPlayer player) {
        return nobleTitleConstant(bridge.isPlayerFemale(level, player).orElse(false));
    }

    /**
     * The Capitals {@code NobleTitle} constant this reward grants for a player of that gender.
     *
     * <p>The one place the two spellings of a rank are decided, so the chat line and the grant cannot
     * disagree about which one the player was given.
     */
    public String nobleTitleConstant(boolean female) {
        String masculine = title.toUpperCase(Locale.ROOT);
        return female
                ? femaleTitle.map(t -> t.toUpperCase(Locale.ROOT)).orElseGet(() -> FEMININE.get(masculine))
                : masculine;
    }

    private static Component titleLabel(String constant) {
        return Component.translatable("mcaquests.capitals.title." + constant.toLowerCase(Locale.ROOT));
    }
}
