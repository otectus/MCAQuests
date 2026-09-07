package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.pack.CompatPacks;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalAllegianceCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalInterregnumCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalPresentCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalRelationCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalRoleCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CompatCapabilityCondition;
import dev.otectus.mcaquests.quest.objective.BreedAnimalsObjective;
import dev.otectus.mcaquests.quest.objective.BuildNearLocationObjective;
import dev.otectus.mcaquests.quest.objective.DefendLocationObjective;
import dev.otectus.mcaquests.quest.objective.EscortEntityObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.ReachLocationObjective;
import dev.otectus.mcaquests.quest.objective.TameAnimalObjective;
import dev.otectus.mcaquests.quest.objective.TradeWithVillagerObjective;
import dev.otectus.mcaquests.quest.objective.VillagerTargeted;
import dev.otectus.mcaquests.quest.reward.CapitalChronicleReward;
import dev.otectus.mcaquests.quest.reward.CapitalTitleReward;
import dev.otectus.mcaquests.quest.reward.CapitalVillagerTitleReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.situation.SituationIds;
import dev.otectus.mcaquests.quest.situation.trigger.CapitalInterregnumTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.CapitalWarTrigger;
import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Capability availability is a lifecycle requirement, independent of changing court politics. */
public final class CapitalsQuestRequirements {
    private CapitalsQuestRequirements() { }

    public static Optional<Component> unavailableReason(QuestDefinition def) {
        CapitalsBridge bridge = CapitalsCompat.bridge();
        return available(def, bridge::has)
                ? Optional.empty()
                : Optional.of(Component.translatable("mcaquests.quest.suspended.compat",
                        Component.literal("MCA Capitals")));
    }

    // Pure seam: allows regression tests to model partial installations without a Minecraft server.
    static boolean available(QuestDefinition def, Predicate<CapitalsCapability> has) {
        return requiredCapabilities(def).stream().allMatch(has)
                && def.conditions().map(c -> conditionAvailable(c, has, false)).orElse(true);
    }

    /** Persist these after resolving a situation template so its shared clock also works offline. */
    public static Set<CapitalsCapability> requiredCapabilities(QuestDefinition def) {
        Set<CapitalsCapability> required = EnumSet.noneOf(CapitalsCapability.class);
        for (QuestObjective objective : def.objectives()) {
            if (objective instanceof VillagerTargeted targeted) target(targeted.targetSelector(), required);
            if (objective instanceof TradeWithVillagerObjective trade) trade.villager().ifPresent(t -> target(t, required));
            if (objective instanceof EscortEntityObjective escort) anchor(escort.destination(), required);
            if (objective instanceof ReachLocationObjective reach) anchor(reach.location(), required);
            if (objective instanceof BuildNearLocationObjective build) anchor(build.location(), required);
            if (objective instanceof DefendLocationObjective defend) anchor(defend.location(), required);
            if (objective instanceof BreedAnimalsObjective breed) breed.near().ifPresent(a -> anchor(a, required));
            if (objective instanceof TameAnimalObjective tame) tame.near().ifPresent(a -> anchor(a, required));
        }
        for (QuestReward reward : def.rewards()) {
            if (reward instanceof CapitalTitleReward) required.add(CapitalsCapability.TITLE_GRANTS);
            if (reward instanceof CapitalChronicleReward) required.add(CapitalsCapability.CHRONICLE);
            if (reward instanceof CapitalVillagerTitleReward title) {
                required.add(CapitalsCapability.VILLAGER_TITLES);
                target(title.villager(), required);
            }
        }
        if (!required.isEmpty()) required.add(CapitalsCapability.REGISTRY);
        return Set.copyOf(required);
    }

    private static void anchor(LocationAnchor anchor, Set<CapitalsCapability> required) {
        anchor.villager().ifPresent(t -> target(t, required));
    }

    private static void target(VillagerTarget target, Set<CapitalsCapability> required) {
        if (target.mode() == VillagerTarget.Mode.CAPITAL_ROLE) required.add(CapitalsCapability.ROLES);
    }

    /** Preserve alternatives and explicit absent-mod gates; never re-test player roles at turn-in. */
    private static boolean conditionAvailable(QuestCondition condition, Predicate<CapitalsCapability> has,
                                               boolean negated) {
        if (condition instanceof NotCondition not) return conditionAvailable(not.condition(), has, !negated);
        if (condition instanceof AllOfCondition all) {
            return negated
                    ? all.conditions().stream().anyMatch(c -> conditionAvailable(c, has, true))
                    : all.conditions().stream().allMatch(c -> conditionAvailable(c, has, false));
        }
        if (condition instanceof AnyOfCondition any) {
            return negated
                    ? any.conditions().stream().allMatch(c -> conditionAvailable(c, has, true))
                    : any.conditions().stream().anyMatch(c -> conditionAvailable(c, has, false));
        }
        if (condition instanceof CompatCapabilityCondition compat) {
            if (!compat.provider().equals("mcacapitals") || compat.present() == negated) return true;
            for (CapitalsCapability capability : CapitalsCapability.values()) {
                if (capability.id().equalsIgnoreCase(compat.capability())) return has.test(capability);
            }
            return false;
        }
        if (condition instanceof CapitalPresentCondition) return has.test(CapitalsCapability.REGISTRY);
        if (condition instanceof CapitalRoleCondition role) {
            return has.test(CapitalsCapability.REGISTRY) && has.test(role.subject() == CapitalRoleCondition.Subject.PLAYER
                    ? CapitalsCapability.PLAYER_TITLES : CapitalsCapability.ROLES);
        }
        if (condition instanceof CapitalAllegianceCondition allegiance) {
            return has.test(CapitalsCapability.ALLEGIANCE) && (allegiance.match() == CapitalAllegianceCondition.Match.ANY
                    || has.test(CapitalsCapability.REGISTRY));
        }
        if (condition instanceof CapitalRelationCondition relation) {
            return has.test(CapitalsCapability.REGISTRY) && has.test(CapitalsCapability.DIPLOMACY)
                    && (relation.other() != CapitalRelationCondition.Other.ALLEGIANCE || has.test(CapitalsCapability.ALLEGIANCE));
        }
        if (condition instanceof CapitalInterregnumCondition) {
            return has.test(CapitalsCapability.REGISTRY) && has.test(CapitalsCapability.INTERREGNUM);
        }
        return true;
    }

    public static boolean isBundled(ResourceLocation id) {
        ResourceLocation source = SituationIds.sourceIdOf(id).orElse(id);
        return source.getNamespace().equals("mcaquests") && (source.getPath().startsWith("compat/capitals/")
                || source.getPath().equals("capitals_the_empty_throne") || source.getPath().equals("capitals_drums_of_war"));
    }

    public static boolean allowsOffer(QuestDefinition def) {
        return (!isBundled(def.id()) || CompatPacks.CAPITALS_COURT.isEnabled(CompatRegistry.get()))
                && unavailableReason(def).isEmpty();
    }

    public static boolean allowsSituation(SituationDefinition def) {
        if (def.trigger() instanceof CapitalInterregnumTrigger
                && !CapitalsCompat.bridge().has(CapitalsCapability.INTERREGNUM)) return false;
        if (def.trigger() instanceof CapitalWarTrigger
                && !CapitalsCompat.bridge().has(CapitalsCapability.DIPLOMACY)) return false;
        return allowsOffer(def.offer().toQuestDefinition(SituationIds.syntheticId(def.id()), def.enabled(), Optional.empty()));
    }
}
