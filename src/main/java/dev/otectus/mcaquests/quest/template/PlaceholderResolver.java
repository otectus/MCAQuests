package dev.otectus.mcaquests.quest.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;

/**
 * Substitutes resolved template values into raw JSON (objectives/rewards) and into dialogue/title text.
 *
 * <p>Two surfaces:
 * <ul>
 *   <li>{@link #substitute(JsonElement)} — walks the raw objective/reward JSON tree; a string value
 *       that is <em>exactly</em> {@code "{var}"} is replaced by that variable's JSON form (a number for
 *       an int variable, an id string for a registry variable), so the unchanged objective/reward
 *       codecs parse it. Unknown whole-token strings are left in place so the subsequent parse fails
 *       loudly (validation reports them).</li>
 *   <li>{@link #substituteLiteral(String)} — renders a dialogue/title string, replacing
 *       {@code {token}} (value) and {@code {token_name}} (registry display name) with components and
 *       honoring {@code {{}}}/{@code }}} escapes.</li>
 * </ul>
 */
public final class PlaceholderResolver {

    private static final String NAME_SUFFIX = "_name";

    private final Map<String, ResolvedValue> values;

    public PlaceholderResolver(ResolvedTemplate resolved) {
        this.values = resolved.values();
    }

    /** Deep-substitutes whole-token {@code "{var}"} string values into their JSON form. */
    public JsonElement substitute(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                out.add(entry.getKey(), substitute(entry.getValue()));
            }
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                out.add(substitute(child));
            }
            return out;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            String token = wholeToken(raw);
            if (token != null) {
                ResolvedValue value = values.get(token);
                if (value != null) {
                    return value.asJson();
                }
                // Unknown token: leave verbatim so the objective/reward parse fails (caught in validation).
            }
        }
        return element;
    }

    /** Renders a literal dialogue/title string with inline {@code {token}}/{@code {token_name}} placeholders. */
    public Component substituteLiteral(String string) {
        MutableComponent out = Component.empty();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        int n = string.length();
        while (i < n) {
            char c = string.charAt(i);
            if (c == '{') {
                if (i + 1 < n && string.charAt(i + 1) == '{') {
                    literal.append('{');
                    i += 2;
                    continue;
                }
                int close = string.indexOf('}', i + 1);
                String token = close > i ? string.substring(i + 1, close) : null;
                if (token != null && isToken(token)) {
                    flush(out, literal);
                    out.append(displayToken(token));
                    i = close + 1;
                    continue;
                }
                literal.append(c);
                i++;
                continue;
            }
            if (c == '}' && i + 1 < n && string.charAt(i + 1) == '}') {
                literal.append('}');
                i += 2;
                continue;
            }
            literal.append(c);
            i++;
        }
        flush(out, literal);
        return out;
    }

    private Component displayToken(String token) {
        ResolvedValue direct = values.get(token);
        if (direct != null) {
            return direct.display();
        }
        if (token.endsWith(NAME_SUFFIX)) {
            ResolvedValue base = values.get(token.substring(0, token.length() - NAME_SUFFIX.length()));
            if (base != null) {
                return base.display();
            }
        }
        return Component.literal("{" + token + "}");
    }

    private static void flush(MutableComponent out, StringBuilder literal) {
        if (literal.length() > 0) {
            out.append(Component.literal(literal.toString()));
            literal.setLength(0);
        }
    }

    /** Returns the token name if {@code raw} is exactly {@code "{name}"}, else null. */
    private static String wholeToken(String raw) {
        if (raw.length() >= 3 && raw.charAt(0) == '{' && raw.charAt(raw.length() - 1) == '}') {
            String inner = raw.substring(1, raw.length() - 1);
            return isToken(inner) ? inner : null;
        }
        return null;
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
