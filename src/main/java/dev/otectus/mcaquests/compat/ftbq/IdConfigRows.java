package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared {@code fillConfigGroup} helper for every FTB editor id field sourced from
 * {@code ClientKnownIds} (spec §20, task M5.2). §20's consumption paragraph is explicit: tasks/
 * rewards consult the client-synced known ids and offer a dropdown when they're non-empty, but
 * <em>free-text entry must always remain possible</em> — ids from a datapack the client hasn't
 * loaded yet (or hasn't synced yet this session) are legitimate and must stay enterable by hand.
 *
 * <p><b>Mechanism (see task M5.2's report for the full source-evidence trail):</b> FTB Library's
 * {@code ConfigGroup} has no single widget that is both a dropdown and a free-text field, so this
 * always adds the free-text {@code addString} row first (unchanged id/name key — every existing
 * editor screen keeps working even before this class existed), then — only when {@code knownIds}
 * is non-empty — adds a <em>second</em>, independent {@code addEnum} row at {@code id +
 * "_dropdown"} offering the synced ids. Both rows close over the same {@code value}/{@code setter}
 * pair, so either one writes the same backing field; {@code ConfigGroup.save} applies every row's
 * setter in the order the rows were added (last write wins if both were touched in one editing
 * session), which is an acceptable, honestly-documented quirk of "two widgets, one field" — not a
 * data-loss risk, since either widget alone fully round-trips the field.
 *
 * <p>Verified against FTB Library {@code NameMap}/{@code EnumConfig} sources that a current value
 * <em>outside</em> the offered set renders and saves without error: {@code EnumConfig.getStringForGUI}
 * / {@code getColor} / {@code getIcon} all go through our own {@code idProvider}/{@code nameProvider}
 * functions (plain functions of the value, not map lookups), and {@code ConfigValue.init} stores
 * whatever initial value is passed in directly — it never validates membership in the
 * {@code NameMap}. The one quirk: {@code ConfigWithVariants.onClicked} (a plain left-click, not the
 * ctrl-click/16+-item search list) advances via {@code NameMap.getNext}, which computes
 * {@code values.indexOf(current)} — {@code -1} for an off-list value — and wraps to index 0, so a
 * single click on the dropdown when the current value is a hand-typed id jumps to the first known
 * id rather than "the next one after the current position" (which doesn't exist). That is
 * {@code ConfigWithVariants}' existing behaviour for every enum-shaped row in FTB Library, not
 * something introduced here, and it never corrupts data — it only changes what the *next* click
 * would pick. Not worth special-casing away.
 */
final class IdConfigRows {

    private IdConfigRows() {
    }

    /** Convenience overload: the known ids double as their own display names (no separate lookup). */
    static void addIdField(ConfigGroup config, String id, String nameKey, String value,
                           Consumer<String> setter, String def, List<String> knownIds) {
        addIdField(config, id, nameKey, value, setter, def, knownIds, Function.identity());
    }

    /**
     * Adds the free-text row for {@code id} (always), and — only when {@code knownIds} is
     * non-empty — an additional dropdown row at {@code id + "_dropdown"} offering those ids, with
     * display names from {@code displayName}. Both rows read the current {@code value} and write
     * through the same {@code setter}.
     */
    static void addIdField(ConfigGroup config, String id, String nameKey, String value,
                           Consumer<String> setter, String def, List<String> knownIds,
                           Function<String, String> displayName) {
        config.addString(id, value, setter, def)
                .setNameKey(nameKey);
        if (knownIds.isEmpty()) {
            return;
        }
        String initial = knownIds.contains(value) ? value : knownIds.get(0);
        NameMap<String> map = NameMap.of(initial, knownIds)
                .id(Function.identity())
                .name(v -> Component.literal(displayName.apply(v)))
                .create();
        config.addEnum(id + "_dropdown", value, setter, map, def)
                .setNameKey(nameKey + "_dropdown");
    }
}
