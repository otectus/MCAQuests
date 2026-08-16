package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
                    ResourceLocation.CODEC.optionalFieldOf("incident")
                            .forGetter(ResolveIncidentReward::incident),
                    Codec.STRING.listOf().optionalFieldOf("status", List.of())
                            .forGetter(ResolveIncidentReward::status),
                    Codec.STRING.listOf().optionalFieldOf("tags", List.of())
                            .forGetter(ResolveIncidentReward::tags),
                    Codec.STRING.optionalFieldOf("resolution", "atoned")
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
        QuestReputation.resolveIncident(player.server, player.getUUID(), community.get(), selector,
                resolution, null);
    }
}
