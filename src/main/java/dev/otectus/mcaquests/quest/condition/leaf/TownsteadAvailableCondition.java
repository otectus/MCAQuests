package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * True when Townstead is installed and bound, and optionally when a named capability is available
 * (Townstead spec §5.1).
 *
 * <p><b>Every bundled Townstead definition opens with this</b>, naming the exact capabilities it goes
 * on to use. That is what lets the whole content pack ship unconditionally: the types register whether
 * or not Townstead is present, so a pack always parses, and a definition whose gate fails simply never
 * becomes eligible instead of surfacing as a broken offer.
 *
 * <pre>{@code {"type": "mcaquests:townstead_available", "capability": "READ_NEEDS"}}</pre>
 *
 * <p>With several capabilities, list them — all must be available:
 *
 * <pre>{@code {"type": "mcaquests:townstead_available", "capabilities": ["READ_NEEDS", "READ_SCHEDULE"]}}</pre>
 */
public record TownsteadAvailableCondition(List<TownsteadCapability> capabilities) implements QuestCondition {

    public static final Codec<TownsteadAvailableCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadCapability.CODEC, "capability")
                            .forGetter(condition -> condition.capabilities.size() == 1
                                    ? Optional.of(condition.capabilities.get(0))
                                    : Optional.empty()),
                    StrictCodecs.strictOptional(TownsteadCapability.CODEC.listOf(), "capabilities", List.of())
                            .forGetter(condition -> condition.capabilities.size() == 1
                                    ? List.of()
                                    : condition.capabilities)
            ).apply(instance, TownsteadAvailableCondition::of));

    /** Folds the singular and plural spellings into one list, so evaluation has a single shape. */
    private static TownsteadAvailableCondition of(Optional<TownsteadCapability> single,
                                                  List<TownsteadCapability> many) {
        if (single.isEmpty()) {
            return new TownsteadAvailableCondition(List.copyOf(many));
        }
        if (many.isEmpty()) {
            return new TownsteadAvailableCondition(List.of(single.get()));
        }
        List<TownsteadCapability> all = new java.util.ArrayList<>(many);
        all.add(0, single.get());
        return new TownsteadAvailableCondition(List.copyOf(all));
    }

    public TownsteadAvailableCondition {
        capabilities = List.copyOf(capabilities);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.TOWNSTEAD_AVAILABLE;
    }

    @Override
    public boolean test(QuestContext context) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable()) {
            return false;
        }
        for (TownsteadCapability capability : capabilities) {
            if (!bridge.has(capability)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.townstead_available");
    }
}
