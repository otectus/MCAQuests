package dev.otectus.mcaquests.quest.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * A template variable that resolves to one flavor phrase chosen from a non-empty {@code options} pool
 * using the offer's {@link QuestContext#stableRandom stable RNG}. Each option is a {@link QuestText}
 * (literal or translation key), so chosen text is itself translatable. Text variables feed dialogue and
 * titles only; they are never substituted into objective/reward JSON.
 */
public record TextVariable(List<QuestText> options) implements TemplateVariable {

    public static final Codec<TextVariable> CODEC = RecordCodecBuilder.<TextVariable>create(instance -> instance.group(
            QuestText.CODEC.listOf().fieldOf("options").forGetter(TextVariable::options)
    ).apply(instance, TextVariable::new)).flatXmap(TextVariable::validate, TextVariable::validate);

    private static DataResult<TextVariable> validate(TextVariable var) {
        if (var.options.isEmpty()) {
            return DataResult.error(() -> "text variable 'options' must be a non-empty list");
        }
        return DataResult.success(var);
    }

    @Override
    public String kindKey() {
        return "text";
    }

    @Override
    public Optional<ResolvedValue> representative() {
        return options.isEmpty() ? Optional.empty() : Optional.of(new ResolvedValue.TextValue(options.get(0)));
    }

    @Override
    public Optional<ResolvedValue> resolve(String name, QuestContext context) {
        if (options.isEmpty()) {
            return Optional.empty();
        }
        Random random = context.stableRandom("var:" + name);
        QuestText chosen = options.get(random.nextInt(options.size()));
        return Optional.of(new ResolvedValue.TextValue(chosen));
    }
}
