package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Warns about {@code translate} keys with no translation behind them.
 *
 * <p>A missing key is not an error — it renders as the raw key, which is ugly but harmless, and a datapack
 * is perfectly entitled to ship a key that only its own resource pack defines. What it usually is, though,
 * is a typo: {@code mcaquests.quest.letter_to_borther.title} sitting in a quest card in the middle of a
 * sentence, with nothing anywhere saying which file it came from.
 *
 * <p><b>Warning-level, and only for the {@code mcaquests} namespace.</b> Third-party keys belong to
 * third-party language files this server may legitimately not have loaded; ours do not, so ours are the
 * ones we can be confident about.
 *
 * <p>Run from {@code /mcaquests validate} rather than at reload, for the same reason the progression
 * cross-references are: {@link Language} is a client-side concept on a dedicated server, so this reports
 * nothing there — which is correct, since nobody on a dedicated server is reading the text anyway.
 */
public final class TranslationKeyValidator {

    private static final String OWN_NAMESPACE = "mcaquests.";

    private TranslationKeyValidator() {
    }

    /** Every {@code mcaquests}-namespaced translate key in the loaded content that has no translation. */
    public static List<String> collectWarnings(Collection<QuestDefinition> quests,
                                               Collection<SituationDefinition> situations) {
        Language language = Language.getInstance();
        // A language with almost nothing in it is a server with no client language loaded, not a pack with
        // a thousand typos. Reporting every key as missing there would be noise of the worst kind.
        if (!language.has("mcaquests.status.no_quests")) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (QuestDefinition quest : quests) {
            check("Quest '" + quest.id() + "'", quest.titleOverride(), quest.dialogue(), language, missing);
        }
        for (SituationDefinition situation : situations) {
            check("Situation '" + situation.id() + "'", situation.offer().title(),
                    situation.offer().dialogue(), language, missing);
        }
        return List.copyOf(missing);
    }

    private static void check(String label, Optional<QuestText> title, Map<String, QuestText> dialogue,
                              Language language, List<String> out) {
        // Sorted so two runs over the same content report in the same order.
        for (String key : new TreeSet<>(dialogue.keySet())) {
            report(label + " dialogue '" + key + "'", dialogue.get(key), language, out);
        }
        title.ifPresent(text -> report(label + " title", text, language, out));
    }

    private static void report(String where, QuestText text, Language language, List<String> out) {
        text.translate()
                .filter(key -> key.startsWith(OWN_NAMESPACE) && !language.has(key))
                .ifPresent(key -> out.add(where + " uses translation key '" + key
                        + "', which no loaded language file defines; it will render as the raw key."));
    }
}
