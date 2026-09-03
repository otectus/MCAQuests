package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * {@code mcareputation:has_incident} — requires the player to have a matching deed on record with the
 * giver's village (spec §29.6).
 *
 * <pre>{@code
 * {
 *   "type": "mcareputation:has_incident",
 *   "incident": "mcareputation:villager_assaulted",
 *   "status": ["active", "apologized"],
 *   "known_to_giver": true
 * }
 * }</pre>
 *
 * <p>This is what makes a restitution quest possible: the villager only offers to let you make amends
 * for something you actually did, and — with {@code known_to_giver} — only for something they actually
 * know about.
 *
 * <h2>Without MCA: Reputation</h2>
 *
 * <p>There is no incident ledger, so this condition is never met and the quest simply never offers
 * itself. That is the honest degradation: the alternative, treating "I cannot tell" as "yes", would
 * have villagers demanding amends for crimes nobody committed. The condition type is registered
 * either way, so a datapack using it parses cleanly on a Quests-only install (§30.2's parse-safety
 * rule, applied here for the same reason).
 */
public record HasIncidentCondition(Optional<ResourceLocation> incident, List<String> status,
                                   List<String> tags, boolean knownToGiver, boolean negate)
        implements QuestCondition {

    public static final MapCodec<HasIncidentCondition> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("incident")
                            .forGetter(HasIncidentCondition::incident),
                    Codec.STRING.listOf().optionalFieldOf("status", List.of())
                            .forGetter(HasIncidentCondition::status),
                    Codec.STRING.listOf().optionalFieldOf("tags", List.of())
                            .forGetter(HasIncidentCondition::tags),
                    Codec.BOOL.optionalFieldOf("known_to_giver", false)
                            .forGetter(HasIncidentCondition::knownToGiver),
                    Codec.BOOL.optionalFieldOf("negate", false).forGetter(HasIncidentCondition::negate)
            ).apply(instance, HasIncidentCondition::new));

    public HasIncidentCondition {
        incident = incident == null ? Optional.empty() : incident;
        status = status == null ? List.of() : List.copyOf(status);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.HAS_INCIDENT;
    }

    @Override
    public boolean test(QuestContext context) {
        Optional<QuestReputation.Community> community = QuestReputation.resolve(context.villager());
        if (community.isEmpty()) {
            return negate; // no village: nothing on record, so "does not have" is trivially true
        }
        IncidentSelector selector = new IncidentSelector(
                incident.map(List::of).orElseGet(List::of), status, tags, knownToGiver, 0L);
        boolean found = QuestReputation.hasIncident(context.player().server, context.player().getUUID(),
                community.get(), selector);
        return negate != found;
    }
}
