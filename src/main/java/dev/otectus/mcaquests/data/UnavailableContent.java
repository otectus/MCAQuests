package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.IntegrationRequirements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Definitions that parsed and were <b>deliberately not loaded</b>, because the optional mod they need is
 * not installed here (or is installed without the capability they read).
 *
 * <p>These are not playable. Nothing offers, assigns, activates, seeds or advances them; they are absent
 * from {@link QuestRegistry}, {@code ProjectRegistry} and {@code SituationRegistry} exactly as if their
 * files did not exist. What this registry keeps is an inert descriptor, for two readers only:
 *
 * <ul>
 *   <li>a record a player already accepted, which shows as <em>paused</em> with the missing mod named
 *       rather than as an unknown quest, and whose owed rewards are deferred rather than failed; and</li>
 *   <li>operators and validators, which must not mistake intentionally excluded content for a dangling
 *       reference or a malformed file.</li>
 * </ul>
 *
 * <p>Rebuilt on every reload from what the loaders saw. Removing or adding a mod jar takes a restart;
 * {@code /reload} re-applies config and datapack changes against the mods already running.
 */
public final class UnavailableContent {

    /** Which registry a descriptor was kept out of. */
    public enum Kind {
        QUEST, PROJECT, SITUATION
    }

    /**
     * One excluded definition.
     *
     * @param source the pack its winning file came from, for diagnostics ({@code "?"} when unknown)
     * @param title  the definition's own title, so a paused record can still be named to its player
     */
    public record Entry(Kind kind, ResourceLocation id, IntegrationRequirements.Unavailable why, String source,
                        net.minecraft.network.chat.Component title) {
    }

    private static volatile Map<Kind, Map<ResourceLocation, Entry>> entries = empty();

    private UnavailableContent() {
    }

    private static Map<Kind, Map<ResourceLocation, Entry>> empty() {
        Map<Kind, Map<ResourceLocation, Entry>> out = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            out.put(kind, Map.of());
        }
        return out;
    }

    /** Replaces one kind's descriptors; called by that kind's loader at the end of every reload. */
    public static synchronized void replace(Kind kind, Map<ResourceLocation, Entry> replacement) {
        Map<Kind, Map<ResourceLocation, Entry>> next = new EnumMap<>(entries);
        next.put(kind, Map.copyOf(replacement));
        entries = next;
    }

    public static Optional<Entry> get(Kind kind, ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(entries.get(kind).get(id));
    }

    public static boolean contains(Kind kind, ResourceLocation id) {
        return get(kind, id).isPresent();
    }

    /** Every descriptor of one kind, in load order. */
    public static Map<ResourceLocation, Entry> all(Kind kind) {
        return entries.get(kind);
    }

    /** Test seam. */
    public static synchronized void clearForTest() {
        entries = empty();
    }

    /** The pack the merged winner for {@code fileId} came from, or {@code "?"}. */
    public static String sourceOf(ResourceManager manager, String directory, ResourceLocation fileId) {
        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), directory + "/" + fileId.getPath() + ".json");
        return manager.getResource(path).map(Resource::sourcePackId).orElse("?");
    }

    /**
     * One INFO line per loader and kind, grouped by the missing integration, plus a DEBUG line each when
     * {@code debugLogging} is on. Excluding content for a mod that is not installed is the normal state
     * of a base installation, not a fault, so it is summarised rather than repeated per file.
     */
    static void report(Kind kind, Map<ResourceLocation, Entry> excluded) {
        if (excluded.isEmpty()) {
            return;
        }
        Map<String, Integer> byIntegration = new TreeMap<>();
        excluded.values().forEach(entry ->
                byIntegration.merge(entry.why().integration().displayName(), 1, Integer::sum));
        McaQuests.LOGGER.info("[MCA: Quests] Not loading {} {} definition(s) that need an optional mod this "
                        + "installation does not have: {}. Records already accepted stay paused, not lost.",
                excluded.size(), kind.name().toLowerCase(java.util.Locale.ROOT), byIntegration);
        if (McaQuestsConfig.COMMON.debugLogging.get()) {
            excluded.values().forEach(entry -> McaQuests.LOGGER.debug("[MCA: Quests]   {} {} ({}; from {})",
                    kind.name().toLowerCase(java.util.Locale.ROOT), entry.id(), entry.why().describe(), entry.source()));
        }
    }

    /** Builder the loaders use; keeps load order. */
    public static final class Collector {
        private final Kind kind;
        private final Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();

        public Collector(Kind kind) {
            this.kind = kind;
        }

        public void add(ResourceLocation id, IntegrationRequirements.Unavailable why, String source,
                        net.minecraft.network.chat.Component title) {
            entries.put(id, new Entry(kind, id, why, source, title));
        }

        public Map<ResourceLocation, Entry> entries() {
            return entries;
        }

        public void publish() {
            report(kind, entries);
            replace(kind, entries);
        }
    }
}
