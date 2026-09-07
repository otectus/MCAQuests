package dev.otectus.mcaquests.quest.reward;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Writes a line about the player into the capital's chronicle (MCA Capitals).
 *
 * <pre>{@code
 * {"type": "mcaquests:capital_chronicle", "key": "mcaquests.chronicle.capitals.royal_escort"}
 * }</pre>
 *
 * <p>{@code key} is a translation key given the player's name and the capital's name, in that order.
 * With {@code "herald": true} (the default) the herald cries it out as well as recording it.
 *
 * <p>Rendered <b>server-side</b>, because Capitals' chronicle stores flat strings rather than
 * components — the line is written once, in the server's locale, and read by everyone in that form.
 */
public record CapitalChronicleReward(String key, boolean herald, Optional<String> fallback) implements QuestReward {

    /** Compatibility for add-ons constructing the original reward shape. */
    public CapitalChronicleReward(String key, boolean herald) {
        this(key, herald, Optional.empty());
    }

    public static final MapCodec<CapitalChronicleReward> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.fieldOf("key").forGetter(CapitalChronicleReward::key),
                    StrictCodecs.strictOptional(Codec.BOOL, "herald", true)
                            .forGetter(CapitalChronicleReward::herald),
                    StrictCodecs.strictOptional(Codec.STRING, "fallback")
                            .forGetter(CapitalChronicleReward::fallback)
            ).apply(instance, CapitalChronicleReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.CAPITAL_CHRONICLE;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.capital_chronicle");
    }

    /** Needs the capital the quest was accepted in, which only the context carries. */
    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager, RewardContext context) {
        CapitalsBridge bridge = CapitalsCompat.bridge();
        if (!bridge.has(CapitalsCapability.CHRONICLE)) {
            McaQuests.LOGGER.warn("[MCA: Quests] Skipping a capital_chronicle reward on quest '{}': MCA "
                    + "Capitals cannot be written to right now.", context.questId());
            return;
        }
        MinecraftServer server = player.getServer();
        ServerLevel level = context.level(player);
        Optional<CapitalRef> capital = CapitalsQueries.capitalForContext(server, context);
        if (level == null || capital.isEmpty()) {
            McaQuests.LOGGER.warn("[MCA: Quests] Skipping a capital_chronicle reward on quest '{}': the "
                    + "village it was accepted in is no longer the seat of a capital.", context.questId());
            return;
        }
        String entry = entryText(player.getGameProfile().getName(),
                CapitalsQueries.capitalName(level, capital.get()).orElse(""));
        bridge.addChronicleEntry(level, capital.get(), entry, herald);
    }

    /** Dedicated servers do not load client resource-pack translations before flattening components. */
    String entryText(String playerName, String capitalName) {
        return Component.translatableWithFallback(key,
                fallback.orElseGet(() -> BundledText.ENGLISH.get(key)), playerName, capitalName).getString();
    }

    private static final class BundledText {
        private static final Map<String, String> ENGLISH = load();

        private static Map<String, String> load() {
            try (InputStream stream = CapitalChronicleReward.class
                    .getResourceAsStream("/assets/mcaquests/lang/en_us.json")) {
                if (stream == null) return Map.of();
                Map<String, String> entries = new HashMap<>();
                JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject().entrySet().forEach(entry -> {
                            if (entry.getKey().startsWith("mcaquests.chronicle.")) {
                                entries.put(entry.getKey(), entry.getValue().getAsString());
                            }
                        });
                return Map.copyOf(entries);
            } catch (Exception exception) {
                McaQuests.LOGGER.warn("[MCA: Quests] Could not read bundled chronicle translations", exception);
                return Map.of();
            }
        }
    }
}
