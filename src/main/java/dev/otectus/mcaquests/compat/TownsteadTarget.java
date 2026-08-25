package dev.otectus.mcaquests.compat;

import com.mojang.serialization.Codec;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which villager a Townstead condition, objective or reward is about (Townstead spec §4.2, §6).
 *
 * <p>Deliberately separate from {@code VillagerTarget} rather than added to its {@code Mode} enum.
 * {@code VillagerTarget} is an established datapack surface with its own validation, and widening it
 * would change what existing packs are allowed to say; this enum names the five selectors the
 * Townstead surface needs and is mapped onto the existing resolver machinery by
 * {@code TownsteadTargetResolver}.
 */
public enum TownsteadTarget {

    /** The quest giver, frozen by UUID when the quest was accepted. The default. */
    GIVER("giver"),

    /** The UUID this objective already bound, if it has one; otherwise the giver. */
    BOUND("bound"),

    /** The MCA family relative the quest is about, resolved once and then frozen. */
    RELATED("related"),

    /** The nearest loaded MCA villager matching the definition's filters, frozen once chosen. */
    NEAREST("nearest"),

    /**
     * Any observed resident of the subject village. Aggregate reads only — a mutation with no single
     * subject is meaningless, so rewards reject this.
     */
    VILLAGE_ANY("village_any");

    private static final Map<String, TownsteadTarget> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TownsteadTarget::id, Function.identity()));

    public static final Codec<TownsteadTarget> CODEC = Codec.STRING.flatXmap(
            raw -> {
                TownsteadTarget target = BY_NAME.get(raw.toLowerCase(Locale.ROOT));
                return target != null
                        ? com.mojang.serialization.DataResult.success(target)
                        : com.mojang.serialization.DataResult.error(
                        () -> "Unknown Townstead target '" + raw + "'; expected one of " + BY_NAME.keySet());
            },
            target -> com.mojang.serialization.DataResult.success(target.id()));

    private final String id;

    TownsteadTarget(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** True when this selector can name exactly one villager, which every mutation requires. */
    public boolean isSingular() {
        return this != VILLAGE_ANY;
    }
}
