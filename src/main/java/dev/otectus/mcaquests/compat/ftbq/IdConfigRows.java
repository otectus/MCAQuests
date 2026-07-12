package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
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
 * always adds a free-text string row first (unchanged id/name key relative to the pre-M5.2
 * screens), then — only when {@code knownIds} is non-empty — a <em>second</em> dropdown row at
 * {@code id + "_dropdown"} offering the synced ids. Both rows write the same underlying field
 * through the same setter.
 *
 * <p><b>Twin-row edit-time sync:</b> two rows over one field is only safe if their caches never
 * disagree: {@code ConfigGroup.save(true)} reapplies <em>every</em> row's own cached value in
 * insertion order on Accept ({@code ConfigGroup.java:261-264} → {@code ConfigValue.applyValue}),
 * so if the user edited only one row, the other row's stale cache would silently revert the edit
 * when applied after it. The rows added here are therefore {@link LinkedStringConfig}/
 * {@link LinkedEnumConfig} twins that propagate every committed edit to their sibling at
 * <em>edit</em> time, making the double-apply idempotent regardless of row order (both caches
 * always hold the same value; the second apply rewrites the identical string — harmless).
 *
 * <p>{@code ConfigValue.setCurrentValue} — the single cache-mutation funnel — is {@code final}
 * ({@code ConfigValue.java:52-59}), so the twins can't hook it directly. Instead they override
 * {@code onClicked} to wrap the {@link ConfigCallback}: every edit path FTB Library has commits
 * via {@code setCurrentValue(...)} immediately followed by {@code callback.save(...)} on the
 * callback passed into {@code onClicked} —
 * <ul>
 *   <li>string overlay Accept: {@code EditStringConfigOverlay.onAccepted}
 *       ({@code EditStringConfigOverlay.java:93-97});</li>
 *   <li>enum cycle-click: {@code ConfigWithVariants.onClicked}
 *       ({@code ConfigWithVariants.java:18-24});</li>
 *   <li>enum search-list picker (16+ entries or ctrl-click): the button callback inside
 *       {@code EnumConfig.onClicked} ({@code EnumConfig.java:66-73});</li>
 * </ul>
 * so the wrapped callback fires at exactly the moment this row's cache was just updated — even on
 * the asynchronous modal/picker paths, which capture the wrapped callback — and pushes the fresh
 * value into the sibling before delegating. The push is a plain cache write (no callback of its
 * own), so there is no recursion to guard against.
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
     * through the same {@code setter}; when both exist they are edit-time-synced twins (see the
     * class javadoc), so editing either keeps the other's cache in agreement.
     */
    static void addIdField(ConfigGroup config, String id, String nameKey, String value,
                           Consumer<String> setter, String def, List<String> knownIds,
                           Function<String, String> displayName) {
        if (knownIds.isEmpty()) {
            config.addString(id, value, setter, def)
                    .setNameKey(nameKey);
            return;
        }
        String initial = knownIds.contains(value) ? value : knownIds.get(0);
        NameMap<String> map = NameMap.of(initial, knownIds)
                .id(Function.identity())
                .name(v -> Component.literal(displayName.apply(v)))
                .create();
        LinkedStringConfig text = new LinkedStringConfig();
        LinkedEnumConfig dropdown = new LinkedEnumConfig(map);
        text.twin = dropdown;
        dropdown.twin = text;
        config.add(id, text, value, setter, def)
                .setNameKey(nameKey);
        config.add(id + "_dropdown", dropdown, value, setter, def)
                .setNameKey(nameKey + "_dropdown");
    }

    /**
     * Wraps {@code callback} so that, whenever an edit path commits through it, this row's
     * just-updated cache is pushed into {@code twin} first — {@code setCurrentValue} is a pure
     * cache write with no callback of its own, so the push can't recurse. Runs on the cancel path
     * too ({@code accepted == false}), where it is a no-op: nothing mutated the cache, so the twin
     * already holds the same value.
     */
    private static ConfigCallback syncing(ConfigValue<String> self, ConfigValue<String> twin,
                                          ConfigCallback callback) {
        return accepted -> {
            twin.setCurrentValue(self.getValue());
            callback.save(accepted);
        };
    }

    /** Free-text half of the twin pair: a plain {@link StringConfig} that mirrors edits to its twin. */
    private static final class LinkedStringConfig extends StringConfig {
        private ConfigValue<String> twin;

        @Override
        public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
            super.onClicked(clickedWidget, button, syncing(this, twin, callback));
        }
    }

    /** Dropdown half of the twin pair: a plain {@link EnumConfig} that mirrors edits to its twin. */
    private static final class LinkedEnumConfig extends EnumConfig<String> {
        private ConfigValue<String> twin;

        LinkedEnumConfig(NameMap<String> nameMap) {
            super(nameMap);
        }

        @Override
        public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
            super.onClicked(clickedWidget, button, syncing(this, twin, callback));
        }
    }
}
