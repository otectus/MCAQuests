package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.KingdomIntegration;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.state.KingdomBindingSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/** Exactly-once political standing reward applied through Ultima Factions when available. */
public record FactionStandingReward(int amount, Optional<ResourceLocation> kingdom, String subject,
                                    Optional<String> description, boolean quiet) implements QuestReward {
    public static final Codec<FactionStandingReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("amount").forGetter(FactionStandingReward::amount),
            StrictCodecs.strictOptional(ResourceLocation.CODEC, "kingdom").forGetter(FactionStandingReward::kingdom),
            StrictCodecs.strictOptional(Codec.STRING, "subject", "giver_residence")
                    .forGetter(FactionStandingReward::subject),
            StrictCodecs.strictOptional(Codec.STRING, "description").forGetter(FactionStandingReward::description),
            StrictCodecs.strictOptional(Codec.BOOL, "quiet", false).forGetter(FactionStandingReward::quiet)
    ).apply(instance, FactionStandingReward::new));

    public FactionStandingReward {
        kingdom = kingdom == null ? Optional.empty() : kingdom;
        description = description == null ? Optional.empty() : description.map(String::strip)
                .filter(value -> !value.isEmpty());
        if (amount == 0) throw new IllegalArgumentException("faction standing reward amount must be non-zero");
        if (!java.util.Set.of("giver_residence", "giver_origin", "giver_location", "player_location")
                .contains(subject)) {
            throw new IllegalArgumentException("Unknown faction standing reward subject: " + subject);
        }
    }

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.FACTION_STANDING;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.faction_standing", amount);
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        // QuestManager supplies the acceptance snapshot and operation identity through grantBound.
    }

    public boolean grantBound(ServerPlayer player, @Nullable Entity villager, RewardContext context,
                              Optional<KingdomBindingSnapshot> accepted, int rewardIndex) {
        Prepared prepared = prepare(player, villager, context, accepted, rewardIndex).orElse(null);
        return prepared != null && KingdomIntegration.grantFactionStanding(player, prepared.kingdom(), amount,
                new ResourceLocation("mcaquests", "quest_reward"), prepared.operationId(), prepared.sourceRevision(),
                prepared.settlementId(), description, quiet);
    }

    Optional<Prepared> prepare(ServerPlayer player, @Nullable Entity villager, RewardContext context,
                               Optional<KingdomBindingSnapshot> accepted, int rewardIndex) {
        KingdomBindingSnapshot snapshot = accepted.orElseGet(() -> KingdomIntegration.capture(subject,
                Optional.empty(), player, villager).orElse(null));
        ResourceLocation target = kingdom.orElse(snapshot == null ? null : snapshot.kingdomId());
        return prepareResolved(target, context, snapshot, rewardIndex);
    }

    static Optional<Prepared> prepareResolved(@Nullable ResourceLocation target, RewardContext context,
                                              @Nullable KingdomBindingSnapshot snapshot, int rewardIndex) {
        if (target == null || context.instance().isEmpty()) return Optional.empty();
        return Optional.of(new Prepared(target,
                KingdomIntegration.rewardOperation(context.instance().get(), context.questId(), rewardIndex),
                snapshot == null ? Optional.empty() : Optional.of(snapshot.settlementId()), 0L));
    }

    record Prepared(ResourceLocation kingdom, UUID operationId, Optional<UUID> settlementId,
                    long sourceRevision) { }
}
