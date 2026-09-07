package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.capitals.CapitalRef;
import dev.otectus.mcaquests.compat.capitals.CapitalsBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.capitals.CapitalsQueries;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.McaConditionCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * True when the giver's capital stands in one of the named diplomatic states with another (MCA Capitals).
 *
 * <pre>{@code
 * {"type": "mcaquests:capital_relation", "other": "any", "state": ["alliance"]}
 * }</pre>
 *
 * <p>{@code other} is {@code any} by default — any other capital in the world will do, which is what
 * "we have allies to send letters to" means. {@code allegiance} asks specifically about the capital the
 * player has sworn to, so a pack can offer content about the player's own two loyalties pulling apart.
 *
 * <p>The states are Capitals' own, spelled lower case. A capital with no relation recorded reads as
 * nothing rather than as {@code peace}: an untouched pair has never had a relationship row written.
 */
public record CapitalRelationCondition(Other other, List<String> states, boolean present) implements QuestCondition {

    /** Compatibility for add-ons constructing the original positive-only condition. */
    public CapitalRelationCondition(Other other, List<String> states) {
        this(other, states, true);
    }

    /** The five states Capitals records between two capitals. */
    public static final Set<String> STATES =
            Set.of("peace", "non_aggression_pact", "alliance", "truce", "war");

    /** Which other capital the relation is measured against. */
    public enum Other {
        ANY,
        ALLEGIANCE;

        public static final Codec<Other> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown capital relation other: '" + name
                                + "' (expected any/allegiance)");
                    }
                },
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    }

    public static final MapCodec<CapitalRelationCondition> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Other.CODEC, "other", Other.ANY)
                            .forGetter(CapitalRelationCondition::other),
                    McaConditionCodecs.validatedNonEmptyList("capital diplomatic state", STATES)
                            .fieldOf("state").forGetter(CapitalRelationCondition::states),
                    StrictCodecs.strictOptional(Codec.BOOL, "present", true)
                            .forGetter(CapitalRelationCondition::present)
            ).apply(instance, CapitalRelationCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.CAPITAL_RELATION;
    }

    @Override
    public boolean test(QuestContext context) {
        CapitalsBridge bridge = CapitalsCompat.bridge();
        if (!bridge.has(CapitalsCapability.REGISTRY) || !bridge.has(CapitalsCapability.DIPLOMACY)
                || other == Other.ALLEGIANCE && !bridge.has(CapitalsCapability.ALLEGIANCE)) {
            return false;
        }
        boolean matching = hasMatchingRelation(context, bridge);
        return bridge.has(CapitalsCapability.REGISTRY) && bridge.has(CapitalsCapability.DIPLOMACY)
                && (other != Other.ALLEGIANCE || bridge.has(CapitalsCapability.ALLEGIANCE))
                && matching == present;
    }

    private boolean hasMatchingRelation(QuestContext context, CapitalsBridge bridge) {
        Entity giver = context.villager();
        if (giver == null || !(giver.level() instanceof ServerLevel level)) {
            return false;
        }
        Optional<CapitalRef> own = CapitalsQueries.giverCapital(giver);
        if (own.isEmpty()) {
            return false;
        }
        UUID mine = own.get().capitalId();
        if (other == Other.ALLEGIANCE) {
            if (context.player() == null) {
                return false;
            }
            return bridge.declaredAllegiance(level, context.player().getUUID())
                    .filter(declared -> !declared.equals(mine))
                    .map(declared -> matches(bridge.diplomaticState(level, mine, declared)))
                    .orElse(false);
        }
        return bridge.allCapitals().stream()
                .map(CapitalRef::capitalId)
                .filter(id -> !id.equals(mine))
                .anyMatch(id -> matches(bridge.diplomaticState(level, mine, id)));
    }

    /** Capitals names its states in upper case; the datapack writes them in lower. */
    private boolean matches(Optional<String> state) {
        return state.map(s -> states.contains(s.toLowerCase(Locale.ROOT))).orElse(false);
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.capital_relation",
                Component.literal(String.join(", ", states)));
    }
}
