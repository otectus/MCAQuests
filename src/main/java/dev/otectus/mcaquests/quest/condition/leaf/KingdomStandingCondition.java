package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.KingdomIntegration;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.kingdom.KingdomGateSpec;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Native local/faction/effective/either/both standing condition under the Ultima namespace. */
public record KingdomStandingCondition(KingdomGateSpec gate) implements QuestCondition {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<KingdomStandingCondition> CODEC = RecordCodecBuilder
            .<KingdomStandingCondition>mapCodec(instance -> instance.group(
                    StrictCodecs.strictOptional(ResourceLocation.CODEC, "gate")
                            .forGetter(value -> value.gate.namedGate()),
                    StrictCodecs.strictOptional(Codec.STRING, "subject", "giver_residence")
                            .forGetter(value -> value.gate.subject()),
                    StrictCodecs.strictOptional(ResourceLocation.CODEC.listOf(), "include", List.of())
                            .forGetter(value -> List.copyOf(value.gate.include())),
                    StrictCodecs.strictOptional(ResourceLocation.CODEC.listOf(), "exclude", List.of())
                            .forGetter(value -> List.copyOf(value.gate.exclude())),
                    StrictCodecs.strictOptional(KingdomGateSpec.UnknownPolicy.CODEC, "when_unknown",
                            KingdomGateSpec.UnknownPolicy.DENY).forGetter(value -> value.gate.whenUnknown()),
                    StrictCodecs.strictOptional(UUID_CODEC, "settlement_id")
                            .forGetter(value -> value.gate.explicitSettlementId()),
                    KingdomGateSpec.StandingGate.CODEC.fieldOf("standing")
                            .forGetter(value -> value.gate.standing().orElseThrow())
            ).apply(instance, (named, subject, include, exclude, unknown, settlement, standing) ->
                    new KingdomStandingCondition(new KingdomGateSpec(named, subject,
                            unique(include, "include"), unique(exclude, "exclude"), unknown, settlement,
                            Optional.of(standing)))))
            .codec();

    private static java.util.Set<ResourceLocation> unique(List<ResourceLocation> values, String field) {
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>(values);
        if (unique.size() != values.size()) throw new IllegalArgumentException(field + " contains duplicates");
        return java.util.Set.copyOf(unique);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.KINGDOM_STANDING;
    }

    @Override
    public boolean test(QuestContext context) {
        if (!KingdomIntegration.allows(gate, context.player(), context.villager())) return false;
        var snapshot = KingdomIntegration.capture(gate.subject(), gate.explicitSettlementId(),
                context.player(), context.villager());
        if (snapshot.isEmpty()) return false;
        return KingdomIntegration.standingAllows(gate.standing().orElseThrow(), snapshot.get(),
                context.player());
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.kingdom_standing");
    }
}
