package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Whether a piece of the <em>bundled</em> Townstead content may be offered (spec §5.10).
 *
 * <p>{@code compat.townstead.contentEnabled} has promised since 1.4.0 that it hides the Townstead
 * quests, projects and situations this mod ships. It only ever hid the situations. This is the rest of
 * that promise, plus the per-theme sub-toggles: "Townstead content" is not one thing, and a server
 * that wants the needs and schedule quests but not the civic identity ones previously had to take all
 * of it or none.
 *
 * <p><b>Bundled only.</b> The gate applies to definitions in the {@code mcaquests} namespace and
 * nothing else. Reaching into a third-party pack and silently disabling half of it from a config option
 * its author never mentioned would be a worse behaviour than the one this fixes.
 *
 * <p>Themes are keyed on the definition's {@code offer_group}, which every bundled Townstead
 * definition declares. An unrecognised or absent group is <b>allowed</b>: a gate that hid content it
 * did not recognise would turn a future authoring slip into invisible missing quests.
 */
public final class TownsteadContentGate {

    /** The category every bundled Townstead quest and situation offer declares. */
    private static final String CATEGORY = "townstead";

    private TownsteadContentGate() {
    }

    /**
     * True when a bundled quest or situation offer may be offered.
     *
     * @param id         the definition id, used only to tell bundled content from a datapack's
     * @param category   the definition's {@code category}
     * @param offerGroup the definition's {@code offer_group}, when it declares one
     */
    public static boolean allowsQuest(ResourceLocation id, Optional<String> category,
                                      Optional<String> offerGroup) {
        if (!isBundledTownstead(id, category)) {
            return true;
        }
        if (!McaQuestsConfig.COMMON.townsteadContentEnabled.get()) {
            return false;
        }
        return switch (offerGroup.orElse("")) {
            case "townstead_need", "townstead_schedule" ->
                    McaQuestsConfig.COMMON.townsteadContentNeedsAndSchedules.get();
            case "townstead_work" -> McaQuestsConfig.COMMON.townsteadContentProfessions.get();
            case "townstead_life", "townstead_season" ->
                    McaQuestsConfig.COMMON.townsteadContentCalendarAndLife.get();
            case "townstead_spirit" -> McaQuestsConfig.COMMON.townsteadContentSpiritAndBuildings.get();
            // Ungrouped bundled content, or a group added later than this switch statement. Allowed,
            // because hiding something nobody chose to hide is the worse failure.
            default -> true;
        };
    }

    /** True when a bundled Townstead village project may be sponsored. */
    public static boolean allowsProject(ResourceLocation id, boolean readsTownstead) {
        if (!isBundled(id) || !readsTownstead) {
            return true;
        }
        return McaQuestsConfig.COMMON.townsteadContentEnabled.get()
                && McaQuestsConfig.COMMON.townsteadContentProjects.get();
    }

    /** True when a bundled Townstead situation may open. */
    public static boolean allowsSituation(ResourceLocation id, boolean readsTownstead) {
        if (!isBundled(id) || !readsTownstead) {
            return true;
        }
        return McaQuestsConfig.COMMON.townsteadContentEnabled.get()
                && McaQuestsConfig.COMMON.townsteadContentSituations.get();
    }

    private static boolean isBundledTownstead(ResourceLocation id, Optional<String> category) {
        return isBundled(id) && category.map(CATEGORY::equals).orElse(false);
    }

    private static boolean isBundled(ResourceLocation id) {
        return id != null && McaQuests.MOD_ID.equals(id.getNamespace());
    }
}
