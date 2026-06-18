package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * A piece of quest dialogue that is either a literal string ({@code {"text": "..."}}) or a
 * translation key ({@code {"translate": "..."}}) — spec sections 9 and 32. Resolved to a
 * {@link Component} server-side and sent to the client ready to render.
 */
public record QuestText(Optional<String> text, Optional<String> translate) {

    public static final Codec<QuestText> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("text").forGetter(QuestText::text),
            Codec.STRING.optionalFieldOf("translate").forGetter(QuestText::translate)
    ).apply(instance, QuestText::new));

    public static QuestText literal(String value) {
        return new QuestText(Optional.of(value), Optional.empty());
    }

    public Component resolve() {
        if (translate.isPresent()) {
            return Component.translatable(translate.get());
        }
        return Component.literal(text.orElse(""));
    }
}
