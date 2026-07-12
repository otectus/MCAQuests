package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.Optional;

/**
 * Optional relationship-chain metadata on a {@link QuestDefinition}. A quest with no {@code chain}
 * block behaves exactly as before; this only adds arc grouping (for UI + offer ordering), linear
 * prerequisites (sugar that compiles into {@code quest_completed} conditions), and forward
 * {@code unlocks} pointers (validation + offer priority). Time limits and other failure states live
 * in the separate {@code failure} block ({@link FailureSpec}).
 *
 * <p>Prerequisites cover the common linear case ("must have completed A"). Branching on outcomes
 * (completed vs failed vs abandoned) is expressed with the explicit {@code quest_failed} /
 * {@code quest_abandoned} / {@code quest_completed} conditions in the {@code conditions} block.
 */
public record ChainSpec(String chain,
                        int stage,
                        Optional<Integer> stageTotal,
                        Optional<QuestText> chapter,
                        Optional<QuestText> relationshipArc,
                        List<ResourceLocation> prerequisites,
                        List<ResourceLocation> unlocks) {

    public static final Codec<ChainSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("chain").forGetter(ChainSpec::chain),
            Codec.INT.optionalFieldOf("stage", 1).forGetter(ChainSpec::stage),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("stage_total").forGetter(ChainSpec::stageTotal),
            QuestText.CODEC.optionalFieldOf("chapter").forGetter(ChainSpec::chapter),
            QuestText.CODEC.optionalFieldOf("relationship_arc").forGetter(ChainSpec::relationshipArc),
            ResourceLocation.CODEC.listOf().optionalFieldOf("prerequisites", List.of()).forGetter(ChainSpec::prerequisites),
            ResourceLocation.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(ChainSpec::unlocks)
    ).apply(instance, ChainSpec::new));

    /**
     * A short context line for the UI, e.g. {@code "The Family Farm — Part 2 of 4: Harvest Day"}.
     * Empty when there is nothing useful to show (no arc name, single stage, no chapter). The
     * {@code resolver} renders inline placeholders such as {@code {player}} in the arc and chapter text.
     */
    public Optional<Component> label(PlaceholderResolver resolver) {
        MutableComponent line = Component.empty();
        boolean any = false;
        if (relationshipArc.isPresent()) {
            line.append(relationshipArc.get().resolve(resolver));
            any = true;
        }
        Optional<Component> part = partLabel();
        if (part.isPresent()) {
            if (any) {
                line.append(Component.literal(" — "));
            }
            line.append(part.get());
            any = true;
        }
        if (chapter.isPresent()) {
            if (any) {
                line.append(Component.literal(": "));
            }
            line.append(chapter.get().resolve(resolver));
            any = true;
        }
        return any ? Optional.of(line) : Optional.empty();
    }

    private Optional<Component> partLabel() {
        if (stageTotal.isPresent()) {
            return Optional.of(Component.translatable("mcaquests.chain.part", stage, stageTotal.get()));
        }
        if (stage > 1) {
            return Optional.of(Component.translatable("mcaquests.chain.part_open", stage));
        }
        return Optional.empty();
    }
}
