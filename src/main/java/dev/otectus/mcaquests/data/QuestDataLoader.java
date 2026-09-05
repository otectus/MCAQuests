package dev.otectus.mcaquests.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.reward.HeartsReward;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Datapack reload listener that loads quest JSON from {@code data/<ns>/mcaquests/quests/**.json}
 * (spec section 10). Reloads with {@code /reload}. A malformed quest is logged and skipped; it never
 * crashes the server unless {@code strictJsonValidation} is enabled (spec sections 10, 26).
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class QuestDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "mcaquests/quests";
    /** A resource id inside a codec error message. Bounded to the characters ids may contain. */
    private static final Pattern RESOURCE_ID = Pattern.compile("([a-z0-9_.-]+):[a-z0-9_./-]+");

    public QuestDataLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new QuestDataLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        // enableDefaultQuestPack: an owner who wants only their own content gets only their
        // own content. A datapack that overrides a bundled file keeps its override.
        files = BuiltinPack.filter(files, manager, DIRECTORY);
        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        Map<ResourceLocation, QuestDefinition> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, String> quarantined = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            // The message is captured as well as logged: a quest that could not be parsed is
            // quarantined under the namespace its error blamed, which is what lets the quest log tell
            // a player "this needs content from X" instead of "unknown quest". See QuestRegistry.
            String[] failure = new String[1];
            Optional<QuestDefinition> parsed = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(message -> {
                        failure[0] = message;
                        recordError(errors, strict, "Quest '" + fileId + "': " + message);
                    });
            if (parsed.isEmpty()) {
                String namespace = offendingNamespace(failure[0]);
                quarantined.put(fileId, namespace);
                logQuarantine(fileId, namespace);
                continue;
            }
            QuestDefinition def = parsed.get();
            if (loaded.containsKey(def.id())) {
                recordError(errors, strict, "Duplicate quest id '" + def.id() + "' (from " + fileId + ")");
                continue;
            }
            warnOnHeartsRange(def);
            loaded.put(def.id(), def);
        }

        // Everything above this point reported through recordError, which logs as it goes. The
        // validators below append straight to the list, so their findings were counted in the summary
        // line and never printed — a real error reading as an unexplained "with 1 error(s)".
        int alreadyLogged = errors.size();
        QuestChainValidator.validate(loaded, errors, warnings);
        TemplateValidator.validate(loaded, errors);
        FailureValidator.validate(loaded, errors);
        ObjectiveValidator.validate(loaded, errors, warnings);
        AgeEligibilityValidator.validate(loaded, warnings);
        // New in 1.4.3, and deliberately lenient for one release: it rejects third-party content that has
        // always loaded, so outside strict mode the author gets a loud line rather than a dead server.
        if (strict) {
            TargetGateValidator.validate(loaded, errors, warnings);
        } else {
            TargetGateValidator.validate(loaded, warnings, warnings);
        }
        errors.subList(alreadyLogged, errors.size())
                .forEach(e -> McaQuests.LOGGER.error("[MCA: Quests] {}", e));
        if (strict && !errors.isEmpty()) {
            throw new QuestValidationException(errors.get(errors.size() - 1));
        }

        QuestRegistry.replaceAll(loaded, errors, warnings, quarantined);
        warnings.forEach(w -> McaQuests.LOGGER.warn("[MCA: Quests] {}", w));
        McaQuests.LOGGER.info("Loaded {} MCA quest(s) with {} error(s), {} warning(s).",
                loaded.size(), errors.size(), warnings.size());
    }

    /**
     * The first {@code namespace:path} in a codec error message, which for a quest that named content
     * from an uninstalled mod is that mod's namespace.
     *
     * <p>A regex over an error string is a heuristic and is treated as one: it is bounded (the two
     * character classes are exactly what a {@code ResourceLocation} allows, so it cannot run away over
     * a long message), it is only ever used to word a diagnostic, and finding nothing is a normal
     * outcome that yields the empty string rather than a guess.
     */
    private static String offendingNamespace(String message) {
        if (message == null) {
            return "";
        }
        Matcher matcher = RESOURCE_ID.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * One WARN per quarantined quest, and only when asked for. A pack that deliberately ships content
     * for a mod the owner has not installed is a supported setup, not a fault, so the default install
     * should not fill its log with it every reload -- but the owner debugging "why is this quest not
     * appearing" needs exactly this line, so it defaults on and can be switched off.
     */
    private static void logQuarantine(ResourceLocation fileId, String namespace) {
        if (!McaQuestsConfig.COMMON.logMissingOptionalContent.get()) {
            return;
        }
        McaQuests.LOGGER.warn("[MCA: Quests] Quest '{}' could not be loaded and is quarantined{}. Any "
                + "copy a player has already accepted is paused rather than lost, and a reload once the "
                + "content is present restores it.",
                fileId, namespace.isEmpty() ? "" : " (content from '" + namespace + "')");
    }

    private static void recordError(List<String> errors, boolean strict, String message) {
        errors.add(message);
        McaQuests.LOGGER.error("[MCA: Quests] {}", message);
        if (strict) {
            throw new QuestValidationException(message);
        }
    }

    /** Non-fatal: warn when a hearts reward exceeds the configured clamp (it will be capped at grant). */
    private static void warnOnHeartsRange(QuestDefinition def) {
        int max = McaQuestsConfig.COMMON.maxHeartsReward.get();
        def.rewards().stream()
                .filter(r -> r instanceof HeartsReward)
                .map(r -> ((HeartsReward) r).amount())
                .filter(amount -> amount > max)
                .forEach(amount -> McaQuests.LOGGER.warn(
                        "[MCA: Quests] Quest '{}' hearts reward {} exceeds maxHeartsReward {}; it will be clamped.",
                        def.id(), amount, max));
    }
}
