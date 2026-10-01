package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.data.StrictCodecs;

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
 *
 * <p><b>That default actually works from 1.7.0.</b> The reward read its optional delta and then passed
 * {@code delta.orElse(0)} into the award, which is an explicit zero override — so an author who named
 * only a deed got a deed worth nothing, and the {@code default_delta} in the incident definition was
 * unreachable through this reward. The omitted case is now carried as omitted the whole way down.
 *
 * <p>{@code incident_profile} names the social profile the deed's evidence should be read under, for a
 * generic incident whose meaning a pack decides (MCA: Reputation 0.6.0, §9.5). Ignored by older
 * Reputation builds and by a Quests-only install, where the deed records exactly as it did before.
 */
public record RecordIncidentReward(ResourceLocation incident, Optional<Integer> delta,
                                   Optional<String> visibility, List<String> tags,
                                   Optional<ResourceLocation> incidentProfile) implements QuestReward {

    public static final Codec<RecordIncidentReward> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("incident").forGetter(RecordIncidentReward::incident),
                    StrictCodecs.strictOptional(Codec.INT, "delta").forGetter(RecordIncidentReward::delta),
                    StrictCodecs.strictOptional(Codec.STRING, "visibility").forGetter(RecordIncidentReward::visibility),
                    StrictCodecs.strictOptional(Codec.STRING.listOf(), "tags", List.of())
                            .forGetter(RecordIncidentReward::tags),
                    StrictCodecs.strictOptional(ResourceLocation.CODEC, "incident_profile")
                            .forGetter(RecordIncidentReward::incidentProfile)
            ).apply(instance, RecordIncidentReward::new));

    public RecordIncidentReward {
        delta = delta == null ? Optional.empty() : delta;
        visibility = visibility == null ? Optional.empty() : visibility;
        tags = tags == null ? List.of() : List.copyOf(tags);
        incidentProfile = incidentProfile == null ? Optional.empty() : incidentProfile;
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
        record(player, villager, null);
    }

    /**
     * The same deed, falling back to the village the quest froze at accept when the giver cannot name
     * one — unloaded, or loaded but homeless and out of range (1.7.1). Until then the deed was dropped
     * in silence in exactly the case {@link RewardContext} exists for, while the quest's own standing
     * and a village-scoped title from the same turn-in both landed.
     */
    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager, RewardContext context) {
        record(player, villager, context);
    }

    private void record(ServerPlayer player, @Nullable Entity villager, @Nullable RewardContext context) {
        Optional<QuestReputation.Community> community = QuestReputation.resolve(villager)
                .or(() -> context == null ? Optional.empty() : context.community());
        if (community.isEmpty()) {
            return;
        }
        ReputationAward.Builder award = ReputationAward
                .builder(player.server, player.getUUID(), community.get().dimension(),
                        community.get().villageId(), QuestReputation.SOURCE)
                .incident(incident)
                // The Optional overload: an author who priced nothing gets the incident definition's
                // own value, and an author who wrote 0 gets a deed that moves no standing.
                .delta(delta)
                .incidentProfile(incidentProfile.orElse(null))
                .visibility(visibility.orElse(null))
                .tags(tags);
        if (villager != null) {
            award.subject(villager.getUUID(),
                    dev.otectus.mcaquests.compat.McaCompat.getVillagerDisplayName(villager).getString(),
                    "giver");
        } else if (context != null) {
            award.subject(context.giverUuid(), context.giverName().getString(), "giver");
        }
        QuestReputation.recordIncident(award.build());
    }
}
