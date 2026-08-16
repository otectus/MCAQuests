package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.ReputationAward;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * {@code mcareputation:record_incident} — writes a named public deed that is not the quest's own
 * completion outcome (spec §29.6).
 *
 * <pre>{@code
 * { "type": "mcareputation:record_incident", "incident": "mcareputation:restitution_completed" }
 * }</pre>
 *
 * <p>Exists for the case the top-level {@code reputation} block cannot express: a quest that produces
 * a <em>second</em>, differently-named story. The canonical example is restitution, where finishing
 * the work both reduces the original assault (via {@code resolve_incident}) and adds a small positive
 * deed of its own — two facts, two ledger lines, one quest.
 *
 * <p>The delta defaults to the incident definition's own, so a pack author normally names the deed and
 * lets the datapack decide what it is worth. Supplying one overrides it, clamped by that definition's
 * {@code max_override_abs}.
 */
public record RecordIncidentReward(ResourceLocation incident, Optional<Integer> delta,
                                   Optional<String> visibility, List<String> tags) implements QuestReward {

    public static final Codec<RecordIncidentReward> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("incident").forGetter(RecordIncidentReward::incident),
                    Codec.INT.lenientOptionalFieldOf("delta").forGetter(RecordIncidentReward::delta),
                    Codec.STRING.lenientOptionalFieldOf("visibility").forGetter(RecordIncidentReward::visibility),
                    Codec.STRING.listOf().lenientOptionalFieldOf("tags", List.of())
                            .forGetter(RecordIncidentReward::tags)
            ).apply(instance, RecordIncidentReward::new));

    public RecordIncidentReward {
        delta = delta == null ? Optional.empty() : delta;
        visibility = visibility == null ? Optional.empty() : visibility;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.RECORD_INCIDENT;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.record_incident");
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        Optional<QuestReputation.Community> community = QuestReputation.resolve(villager);
        if (community.isEmpty()) {
            return;
        }
        ReputationAward.Builder award = ReputationAward
                .builder(player.server, player.getUUID(), community.get().dimension(),
                        community.get().villageId(), QuestReputation.SOURCE)
                .incident(incident)
                .delta(delta.orElse(0))
                .visibility(visibility.orElse(null))
                .tags(tags);
        if (villager != null) {
            award.subject(villager.getUUID(),
                    dev.otectus.mcaquests.compat.McaCompat.getVillagerDisplayName(villager).getString(),
                    "giver");
        }
        QuestReputation.recordIncident(award.build());
    }
}
