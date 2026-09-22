package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.data.StrictCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import dev.otectus.mcaquests.quest.reputation.ReputationDedupe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code mcareputation:resolve_incident} — marks a past deed apologised for, atoned for, forgiven, or
 * disproven (spec §29.6).
 *
 * <pre>{@code
 * {
 *   "type": "mcareputation:resolve_incident",
 *   "incident": "mcareputation:villager_assaulted",
 *   "status": ["active", "apologized"],
 *   "resolution": "atoned"
 * }
 * }</pre>
 *
 * <p>This is the payoff of a restitution quest: doing the work reduces the standing penalty of the
 * thing you did, without erasing the record that you did it.
 *
 * <h2>Two safety properties</h2>
 *
 * <ul>
 *   <li><b>The selector must narrow something.</b> A reward that names no incident, status, or tag is
 *       refused with a warning rather than picking one arbitrarily — the difference between "atone for
 *       the assault" and "atone for whatever" is not a detail (§29.6).</li>
 *   <li><b>Resolving is idempotent.</b> The backend refuses a status that is not strictly stronger
 *       than the current one, so a repeatable restitution quest cannot ratchet one incident through
 *       the same reduction twice (§33 rule 14).</li>
 * </ul>
 *
 * <p>Without MCA: Reputation there is nothing to resolve and this is a silent no-op, so a pack that
 * uses it still loads and plays on a Quests-only install.
 */
public record ResolveIncidentReward(Optional<ResourceLocation> incident, List<String> status,
                                    List<String> tags, String resolution) implements QuestReward {

    public static final Codec<ResolveIncidentReward> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(ResourceLocation.CODEC, "incident")
                            .forGetter(ResolveIncidentReward::incident),
                    StrictCodecs.strictOptional(Codec.STRING.listOf(), "status", List.of())
                            .forGetter(ResolveIncidentReward::status),
                    StrictCodecs.strictOptional(Codec.STRING.listOf(), "tags", List.of())
                            .forGetter(ResolveIncidentReward::tags),
                    StrictCodecs.strictOptional(Codec.STRING, "resolution", "atoned")
                            .forGetter(ResolveIncidentReward::resolution)
            ).apply(instance, ResolveIncidentReward::new));

    public ResolveIncidentReward {
        incident = incident == null ? Optional.empty() : incident;
        status = status == null ? List.of() : List.copyOf(status);
        tags = tags == null ? List.of() : List.copyOf(tags);
        resolution = resolution == null || resolution.isBlank()
                ? "atoned" : resolution.toLowerCase(Locale.ROOT);
    }

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.RESOLVE_INCIDENT;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.resolve_incident." + resolution);
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        resolve(player, villager, null, null);
    }

    /**
     * The same resolution, keyed to the quest copy that earned it (1.7.0).
     *
     * <p>The key is what lets the backend <em>bind</em> the incident: the selector discovers a deed,
     * and this operation identity settles that one deed, so a retry after a crash cannot atone for a
     * different incident than the one the reward was granted for, and a replay answers from the
     * receipt instead of ratcheting the same record twice. A repeatable quest accepted again is a new
     * copy, hence a new key, hence free to atone for the next deed.
     */
    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager, RewardContext context) {
        resolve(player, villager, context == null ? null : context.questId(),
                context == null ? null : context.instance().orElse(null));
    }

    private void resolve(ServerPlayer player, @Nullable Entity villager,
                         @Nullable ResourceLocation questId, @Nullable UUID instance) {
        Optional<QuestReputation.Community> community = QuestReputation.resolve(villager);
        if (community.isEmpty()) {
            return;
        }
        IncidentSelector selector = new IncidentSelector(
                incident.map(List::of).orElseGet(List::of), status, tags, false, 0L);
        if (selector.isEmpty()) {
            McaQuests.LOGGER.warn("[MCA: Quests] a resolve_incident reward names no incident, status, or "
                    + "tag; refusing to resolve an arbitrary deed. Add an \"incident\" field.");
            return;
        }
        String operationKey = questId == null
                ? null
                : ReputationDedupe.incidentResolution(questId, instance, resolution);
        QuestReputation.resolveIncident(player.server, player.getUUID(), community.get(), selector,
                resolution, operationKey, villager);
    }
}
