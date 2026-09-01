package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * How a quest may be turned in (spec section 17): {@code {"turn_in": {"mode": "...", "professions": [...]}}}.
 * {@code professions} only applies to {@link TurnInMode#SPECIFIED_PROFESSION}.
 *
 * <p>The mode is optional, because the config key {@code requireOriginalVillagerForTurnIn} decides what a
 * quest that does not state one means. That key had been declared and documented since the first release
 * and read nowhere; {@code CONFIG.md} described it as "quests must be handed in to the villager who gave
 * them (unless the quest overrides via turn_in.mode)", which is precisely a default, so that is what it
 * now is. Telling "the author wrote original_giver" apart from "the author wrote nothing" is why this is
 * an {@link Optional} — and at the shipped default of {@code true} the answer is unchanged, so no existing
 * pack behaves differently.
 */
public record TurnInSpec(Optional<TurnInMode> declaredMode, List<ResourceLocation> professions) {

    public static final TurnInSpec DEFAULT = new TurnInSpec(Optional.empty(), List.of());

    /** The pre-1.4.3 shape, for code that builds a spec with an explicit mode. */
    public TurnInSpec(TurnInMode mode, List<ResourceLocation> professions) {
        this(Optional.of(mode), professions);
    }

    public static final Codec<TurnInSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TurnInMode.CODEC.optionalFieldOf("mode").forGetter(TurnInSpec::declaredMode),
            ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(TurnInSpec::professions)
    ).apply(instance, TurnInSpec::new));

    /**
     * Where this quest may be handed in: what the pack said, or what the server owner configured.
     *
     * <p>{@code requireOriginalVillagerForTurnIn = true} (the default) means an unstated mode is
     * {@code original_giver}, exactly as it always was. {@code false} means an unstated mode is
     * {@code any_villager}, for a server that would rather players never had to walk back.
     */
    public TurnInMode mode() {
        return declaredMode.orElseGet(TurnInSpec::configuredDefaultMode);
    }

    private static TurnInMode configuredDefaultMode() {
        try {
            return dev.otectus.mcaquests.McaQuestsConfig.COMMON.requireOriginalVillagerForTurnIn.get()
                    ? TurnInMode.ORIGINAL_GIVER : TurnInMode.ANY_VILLAGER;
        } catch (RuntimeException e) {
            // No config attached (a unit test): answer what the mod hardcoded before this key was live.
            return TurnInMode.ORIGINAL_GIVER;
        }
    }
}
