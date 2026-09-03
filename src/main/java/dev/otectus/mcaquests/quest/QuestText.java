package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * A piece of quest dialogue that is either a literal string ({@code {"text": "..."}}) or a
 * translation key ({@code {"translate": "..."}}) — spec sections 9 and 32. Resolved to a
 * {@link Component} server-side and sent to the client ready to render.
 *
 * <p>The optional {@code with} list supplies ordered arguments for a {@code translate} line so a
 * template can fill a translation key with resolved placeholder values
 * ({@code {"translate":"...","with":["{count}","{crop_name}"]}}) while keeping the key translatable.
 * Literal {@code text} lines resolve inline {@code {token}} placeholders directly. {@code with} is
 * ignored by hand-authored (non-template) quests, which call the no-arg {@link #resolve()}.
 */
public record QuestText(Optional<String> text, Optional<String> translate, List<String> with) {

    public static final MapCodec<QuestText> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.lenientOptionalFieldOf("text").forGetter(QuestText::text),
            Codec.STRING.lenientOptionalFieldOf("translate").forGetter(QuestText::translate),
            Codec.STRING.listOf().lenientOptionalFieldOf("with", List.of()).forGetter(QuestText::with)
    ).apply(instance, QuestText::new));

    public static final Codec<QuestText> CODEC = MAP_CODEC.codec();

    public static QuestText literal(String value) {
        return new QuestText(Optional.of(value), Optional.empty(), List.of());
    }

    /** Plain resolution for hand-authored quests (no placeholders). */
    public Component resolve() {
        if (translate.isPresent()) {
            return Component.translatable(translate.get());
        }
        return Component.literal(text.orElse(""));
    }

    /**
     * Placeholder-aware resolution for templates. A {@code translate} line feeds each {@code with}
     * entry (resolved as a literal-with-tokens) as a positional argument, preserving translation; a
     * {@code text} line substitutes its inline {@code {token}}/{@code {token_name}} placeholders.
     */
    public Component resolve(PlaceholderResolver resolver) {
        if (translate.isPresent()) {
            if (with.isEmpty()) {
                return Component.translatable(translate.get());
            }
            Object[] args = with.stream().map(resolver::substituteLiteral).toArray();
            return Component.translatable(translate.get(), args);
        }
        if (text.isPresent()) {
            return resolver.substituteLiteral(text.get());
        }
        return Component.empty();
    }
}
