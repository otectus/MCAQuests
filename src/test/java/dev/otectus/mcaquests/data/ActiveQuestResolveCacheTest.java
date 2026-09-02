package dev.otectus.mcaquests.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.template.ResolvedTemplate;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * An accepted template quest caches the definition it resolved to, so the per-second progress tick does
 * not re-parse JSON. That cache was never invalidated: a {@code /reload} that reshaped the template kept
 * being answered with the pre-reload objectives and rewards until the world was restarted. It now
 * follows {@link QuestRegistry#generation()}, which is why this test lives in the {@code data} package —
 * {@code replaceAll} is package-private.
 */
class ActiveQuestResolveCacheTest {

    private static final Path TEMPLATE = Path.of(
            "src/main/resources/data/mcaquests/mcaquests/quests/templates/farmer_crop_request.json");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("a reload re-resolves a template quest a player is already holding")
    void reloadInvalidatesTheResolvedCache() {
        QuestDefinition before = parse(TEMPLATE);
        ActiveQuest held = ActiveQuest.create(
                before.id(),
                UUID.randomUUID(),
                Component.literal("Anna"),
                new ResourceLocation("minecraft", "farmer"),
                new ResourceLocation("minecraft", "overworld"),
                0L,
                before.objectives().size(),
                new ResolvedTemplate(new LinkedHashMap<>()));

        QuestDefinition first = held.resolve(before);
        assertSame(first, held.resolve(before),
                "within one generation the resolved definition is cached, not rebuilt");

        // What /reload does: a freshly parsed catalogue swapped in, bumping the generation.
        QuestDefinition after = parse(TEMPLATE);
        Map<ResourceLocation, QuestDefinition> loaded = new LinkedHashMap<>();
        loaded.put(after.id(), after);
        QuestRegistry.replaceAll(loaded, List.of(), List.of());

        QuestDefinition second = held.resolve(after);
        assertNotSame(first, second, "a reload must reach a quest that is already accepted");
        assertSame(second, held.resolve(after), "and the new answer is cached in turn");
    }

    private static QuestDefinition parse(Path file) {
        String raw;
        try {
            raw = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        DataResult<QuestDefinition> result =
                QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(raw));
        return result.result().orElseThrow(() -> new AssertionError(
                file + " did not parse: " + result.error().map(DataResult.PartialResult::message).orElse("?")));
    }
}
