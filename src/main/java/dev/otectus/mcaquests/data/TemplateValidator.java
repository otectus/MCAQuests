package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.reward.RewardTypes;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import dev.otectus.mcaquests.quest.template.RegistryVariable;
import dev.otectus.mcaquests.quest.template.ResolvedTemplate;
import dev.otectus.mcaquests.quest.template.ResolvedValue;
import dev.otectus.mcaquests.quest.template.TemplateSpec;
import dev.otectus.mcaquests.quest.template.TemplateVariable;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validation of {@code template} blocks, run after all quests load (alongside {@link QuestChainValidator}).
 * Reports — naming the quest and field — empty/unknown registry pools, {@code {token}} placeholders that
 * reference no declared variable, and substituted objective/reward JSON that fails to parse. Findings are
 * appended to the same list surfaced by {@code /mcaquests validate}; under {@code strictJsonValidation}
 * the loader turns the list fatal. Runtime resolution additionally fails safe (skips the offer) if a pool
 * is empty in a particular world, so these are load-time warnings, not the only line of defence.
 */
public final class TemplateValidator {

    private static final String NAME_SUFFIX = "_name";

    private TemplateValidator() {
    }

    public static void validate(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors) {
        for (QuestDefinition def : loaded.values()) {
            def.template().ifPresent(spec -> validateOne(def, spec, errors));
        }
    }

    private static void validateOne(QuestDefinition def, TemplateSpec spec, List<String> errors) {
        ResourceLocation id = def.id();
        Set<String> declared = spec.variables().keySet();

        if (!def.objectives().isEmpty()) {
            errors.add("Quest '" + id + "': has both a 'template' block and top-level 'objectives'; "
                    + "the top-level objectives are ignored (templates objectives live under 'template.objectives').");
        }

        // 1) Registry-pool sanity (item/block/entity are known at load; biome/dimension are format-only).
        spec.variables().forEach((name, variable) -> validatePool(id, name, variable, errors));

        // 2) Every {token} used in objectives/rewards/dialogue/title must name a declared variable.
        Set<String> referenced = new LinkedHashSet<>();
        collectTokens(spec.objectives(), referenced);
        collectTokens(spec.rewards(), referenced);
        def.titleOverride().ifPresent(text -> collectTokens(text, referenced));
        def.dialogue().values().forEach(text -> collectTokens(text, referenced));
        for (String token : referenced) {
            String base = token.endsWith(NAME_SUFFIX) ? token.substring(0, token.length() - NAME_SUFFIX.length()) : token;
            if (!declared.contains(token) && !declared.contains(base)) {
                errors.add("Quest '" + id + "': template placeholder '{" + token + "}' references no declared variable.");
            }
        }

        // 3) Trial substitution + parse with representative values (skipped when a pool is only a dynamic tag).
        Map<String, ResolvedValue> sample = new LinkedHashMap<>();
        boolean complete = true;
        for (Map.Entry<String, TemplateVariable> entry : spec.variables().entrySet()) {
            Optional<ResolvedValue> rep = entry.getValue().representative();
            if (rep.isEmpty()) {
                complete = false;
                break;
            }
            sample.put(entry.getKey(), rep.get());
        }
        if (complete) {
            PlaceholderResolver resolver = new PlaceholderResolver(new ResolvedTemplate(sample));
            ObjectiveTypes.CODEC.listOf().parse(JsonOps.INSTANCE, resolver.substitute(spec.objectives()))
                    .error().ifPresent(e -> errors.add("Quest '" + id + "': template objectives do not parse after "
                            + "substitution: " + e.message()));
            RewardTypes.CODEC.listOf().parse(JsonOps.INSTANCE, resolver.substitute(spec.rewards()))
                    .error().ifPresent(e -> errors.add("Quest '" + id + "': template rewards do not parse after "
                            + "substitution: " + e.message()));
        }
    }

    private static void validatePool(ResourceLocation questId, String name, TemplateVariable variable, List<String> errors) {
        if (!(variable instanceof RegistryVariable registry) || !registry.kind().isStatic()) {
            return; // int/text validated by their codecs; biome/dimension pools are format-only
        }
        for (ResourceLocation rid : registry.ids()) {
            if (!registry.kind().idExistsForValidation(rid)) {
                errors.add("Quest '" + questId + "': template variable '" + name + "' lists unknown "
                        + registry.kind().key() + " id '" + rid + "'.");
            }
        }
        for (ResourceLocation tag : registry.tags()) {
            if (registry.kind().staticMembers(tag).isEmpty()) {
                errors.add("Quest '" + questId + "': template variable '" + name + "' uses "
                        + registry.kind().key() + " tag '" + tag + "' which is empty or unknown.");
            }
        }
    }

    // ---------------------------------------------------------------- token scanning

    private static void collectTokens(QuestText text, Set<String> out) {
        text.text().ifPresent(value -> collectTokens(value, out));
        text.with().forEach(value -> collectTokens(value, out));
    }

    private static void collectTokens(JsonElement element, Set<String> out) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(e -> collectTokens(e.getValue(), out));
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectTokens(child, out);
            }
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            collectTokens(element.getAsString(), out);
        }
    }

    /** Extracts {@code {token}} names from a string, honouring {@code {{} } escapes (mirrors PlaceholderResolver). */
    private static void collectTokens(String string, Set<String> out) {
        int i = 0;
        int n = string.length();
        while (i < n) {
            char c = string.charAt(i);
            if (c == '{') {
                if (i + 1 < n && string.charAt(i + 1) == '{') {
                    i += 2;
                    continue;
                }
                int close = string.indexOf('}', i + 1);
                if (close > i) {
                    String token = string.substring(i + 1, close);
                    if (isToken(token)) {
                        out.add(token);
                    }
                    i = close + 1;
                    continue;
                }
            }
            i++;
        }
    }

    private static boolean isToken(String token) {
        if (token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(c == '_' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }
}
