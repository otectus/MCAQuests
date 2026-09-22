package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.ProjectPhase;
import dev.otectus.mcaquests.project.SharedReward;
import dev.otectus.mcaquests.project.objective.ProjectObjective;
import dev.otectus.mcaquests.project.objective.TownsteadProjectObjective;
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
import dev.otectus.mcaquests.quest.condition.leaf.TownsteadAvailableCondition;
import dev.otectus.mcaquests.quest.condition.leaf.TownsteadBuildingCondition;
import dev.otectus.mcaquests.quest.condition.leaf.TownsteadProfessionTrackCondition;
import dev.otectus.mcaquests.quest.condition.leaf.TownsteadSkillCondition;
import dev.otectus.mcaquests.quest.condition.leaf.TownsteadSpiritCondition;
import dev.otectus.mcaquests.quest.condition.leaf.TownsteadValueCondition;
import dev.otectus.mcaquests.quest.objective.BreedAnimalsObjective;
import dev.otectus.mcaquests.quest.objective.BuildNearLocationObjective;
import dev.otectus.mcaquests.quest.objective.DefendLocationObjective;
import dev.otectus.mcaquests.quest.objective.EscortEntityObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.ReachLocationObjective;
import dev.otectus.mcaquests.quest.objective.TameAnimalObjective;
import dev.otectus.mcaquests.quest.objective.TownsteadObjective;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a definition cannot be played without, and whether this installation has it.
 *
 * <p>One contract for every optional integration whose content ships in this jar or in a datapack:
 * <b>a definition that needs Townstead or MCA Capitals is not loaded as playable content unless that
 * mod is installed and the capabilities the definition reads are bound.</b> The loaders ask this class
 * before registering a quest, project or situation; a definition it rejects becomes an inert
 * {@code UnavailableContent} descriptor instead, so a record a player already accepted can say why it
 * is paused while nothing new of the kind is offered.
 *
 * <h2>Derived from the typed content, never from names</h2>
 *
 * <p>The integration objective types live in the {@code mcaquests} namespace and a datapack may put a
 * Townstead objective in any file it likes, so neither a namespace nor a path says anything. What does
 * is the content: an objective that reads Townstead, a location anchored on a Townstead building, a
 * mandatory condition that asks Townstead something. For a project that means <em>every</em> phase:
 * a vanilla donation in phase one does not make a Townstead phase two playable on a base installation.
 *
 * <h2>Mandatory, not merely mentioned</h2>
 *
 * <ul>
 *   <li>Objectives are all mandatory, so any objective reading an integration requires it.</li>
 *   <li>A condition requires an integration only in a position the definition cannot be offered
 *       without: an {@code any_of} requires what <em>every</em> branch requires, and a Townstead
 *       condition under {@code not} is an absent-mod gate, not a requirement. Capitals conditions keep
 *       the rules they shipped with in 1.6.0 (moved here verbatim from
 *       {@code CapitalsQuestRequirements}).</li>
 *   <li>Townstead <em>rewards</em> are optional side effects: a Townstead reward that cannot apply is
 *       skipped and the quest completes regardless, so it never makes a core quest unavailable. Capitals
 *       rewards were declared required in 1.6.0 and stay so.</li>
 * </ul>
 *
 * <p>Several integrations combine with AND. Content that needs both mods needs both; content needing
 * only one never waits on the other.
 */
public final class IntegrationRequirements {

    private IntegrationRequirements() {
    }

    /** The optional integrations whose content is gated here, by their real loader id. */
    public enum Integration {
        TOWNSTEAD("townstead", "Townstead"),
        CAPITALS("mcacapitals", "MCA Capitals");

        private final String modId;
        private final String displayName;

        Integration(String modId, String displayName) {
            this.modId = modId;
            this.displayName = displayName;
        }

        public String modId() {
            return modId;
        }

        /** A proper name, deliberately untranslated: it names a mod, not a concept. */
        public String displayName() {
            return displayName;
        }

        public static Optional<Integration> byModId(String modId) {
            for (Integration integration : values()) {
                if (integration.modId.equals(modId)) {
                    return Optional.of(integration);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * What the installation offers. A pure seam, so every row of the installation matrix can be tested
     * without Forge, a mod file or a server.
     */
    public interface Availability {

        /** The mod is installed and its adapter bound (fully or partly). */
        boolean usable(Integration integration);

        /** One capability of a usable integration. Ids are the adapter's own, compared case-insensitively. */
        boolean has(Integration integration, String capability);

        /** The live installation. */
        static Availability live() {
            return LIVE;
        }

        /** {@code base} for {@code only}, and every other integration fully present. */
        static Availability onlyRestricting(Integration only, Availability base) {
            return new Availability() {
                @Override
                public boolean usable(Integration integration) {
                    return integration != only || base.usable(integration);
                }

                @Override
                public boolean has(Integration integration, String capability) {
                    return integration != only || base.has(integration, capability);
                }
            };
        }
    }

    private static final Availability LIVE = new Availability() {
        @Override
        public boolean usable(Integration integration) {
            return switch (integration) {
                case TOWNSTEAD -> TownsteadBridge.Holder.get().isAvailable();
                case CAPITALS -> {
                    CompatStatus status = CapitalsCompat.bridge().status();
                    yield status == CompatStatus.FULL || status == CompatStatus.PARTIAL;
                }
            };
        }

        @Override
        public boolean has(Integration integration, String capability) {
            return switch (integration) {
                case TOWNSTEAD -> townsteadCapability(capability)
                        .map(TownsteadBridge.Holder.get()::has).orElse(false);
                case CAPITALS -> {
                    CapitalsBridge bridge = CapitalsCompat.bridge();
                    yield capitalsCapability(capability).map(bridge::has).orElse(false);
                }
            };
        }
    };

    /**
     * Why a definition cannot be played here: the first integration it needs that is missing, and the
     * capabilities it needs from it that are not bound (empty when the mod itself is absent).
     */
    public record Unavailable(Integration integration, Set<String> missingCapabilities) {

        public Unavailable {
            missingCapabilities = Set.copyOf(missingCapabilities);
        }

        /** The player-facing reason, in the words "Quest paused" has used since 1.6.0. */
        public Component reason() {
            return Component.translatable("mcaquests.quest.suspended.compat",
                    Component.literal(integration.displayName()));
        }

        /** One line for logs and operator diagnostics. */
        public String describe() {
            return missingCapabilities.isEmpty()
                    ? integration.displayName() + " is not installed or not bound"
                    : integration.displayName() + " is missing " + String.join(", ", new TreeSet<>(missingCapabilities));
        }
    }

    // ------------------------------------------------------------------ entry points

    /** Why {@code def} cannot be played on the live installation, if it cannot. */
    public static Optional<Unavailable> unavailable(QuestDefinition def) {
        return unavailable(def, Availability.live());
    }

    public static Optional<Unavailable> unavailable(ProjectDefinition def) {
        return unavailable(def, Availability.live());
    }

    public static Optional<Unavailable> unavailable(SituationDefinition def) {
        return unavailable(def, Availability.live());
    }

    public static Optional<Unavailable> unavailable(QuestDefinition def, Availability availability) {
        return firstMissing(integration -> questAvailable(def, Availability.onlyRestricting(integration, availability)),
                integration -> missingCapabilities(questCapabilities(def, integration), integration, availability),
                availability);
    }

    public static Optional<Unavailable> unavailable(ProjectDefinition def, Availability availability) {
        return firstMissing(integration -> projectAvailable(def, Availability.onlyRestricting(integration, availability)),
                integration -> missingCapabilities(projectCapabilities(def, integration), integration, availability),
                availability);
    }

    public static Optional<Unavailable> unavailable(SituationDefinition def, Availability availability) {
        return firstMissing(integration -> situationAvailable(def, Availability.onlyRestricting(integration, availability)),
                integration -> missingCapabilities(situationCapabilities(def, integration), integration, availability),
                availability);
    }

    /** True when {@code def} cannot be played without {@code integration} (whatever is installed now). */
    public static boolean dependsOn(QuestDefinition def, Integration integration) {
        return !questAvailable(def, Availability.onlyRestricting(integration, ABSENT));
    }

    public static boolean dependsOn(ProjectDefinition def, Integration integration) {
        return !projectAvailable(def, Availability.onlyRestricting(integration, ABSENT));
    }

    public static boolean dependsOn(SituationDefinition def, Integration integration) {
        return !situationAvailable(def, Availability.onlyRestricting(integration, ABSENT));
    }

    /** Every integration {@code def} cannot be played without, in declaration order. */
    public static List<Integration> dependencies(ProjectDefinition def) {
        List<Integration> out = new ArrayList<>();
        for (Integration integration : Integration.values()) {
            if (dependsOn(def, integration)) {
                out.add(integration);
            }
        }
        return out;
    }

    public static List<Integration> dependencies(QuestDefinition def) {
        List<Integration> out = new ArrayList<>();
        for (Integration integration : Integration.values()) {
            if (dependsOn(def, integration)) {
                out.add(integration);
            }
        }
        return out;
    }

    public static List<Integration> dependencies(SituationDefinition def) {
        List<Integration> out = new ArrayList<>();
        for (Integration integration : Integration.values()) {
            if (dependsOn(def, integration)) {
                out.add(integration);
            }
        }
        return out;
    }

    /** Nothing installed at all. */
    private static final Availability ABSENT = new Availability() {
        @Override
        public boolean usable(Integration integration) {
            return false;
        }

        @Override
        public boolean has(Integration integration, String capability) {
            return false;
        }
    };

    private interface Check {
        boolean passes(Integration restricted);
    }

    private interface Missing {
        Set<String> of(Integration integration);
    }

    /** Attributes a failure to the integration that causes it, one integration restricted at a time. */
    private static Optional<Unavailable> firstMissing(Check check, Missing missing, Availability availability) {
        for (Integration integration : Integration.values()) {
            if (!check.passes(integration)) {
                return Optional.of(new Unavailable(integration,
                        availability.usable(integration) ? missing.of(integration) : Set.of()));
            }
        }
        return Optional.empty();
    }

    private static Set<String> missingCapabilities(Set<String> wanted, Integration integration,
                                                   Availability availability) {
        Set<String> missing = new TreeSet<>();
        for (String capability : wanted) {
            if (!availability.has(integration, capability)) {
                missing.add(capability);
            }
        }
        return missing;
    }

    // ------------------------------------------------------------------ quests

    static boolean questAvailable(QuestDefinition def, Availability availability) {
        return requirementsMet(questRequirements(def), availability)
                && def.conditions().map(c -> conditionAvailable(c, availability, false)).orElse(true);
    }

    /** The flat, unconditional requirements of a quest: objectives, anchors, targets and required rewards. */
    private static List<Requirement> questRequirements(QuestDefinition def) {
        List<Requirement> out = new ArrayList<>();
        for (QuestObjective objective : def.objectives()) {
            objectiveRequirements(objective, out);
        }
        for (QuestReward reward : def.rewards()) {
            rewardRequirements(reward, out);
        }
        // Every capital capability implies the registry, exactly as 1.6.0 derived it.
        if (out.stream().anyMatch(r -> r.integration() == Integration.CAPITALS)) {
            out.add(new Requirement(Integration.CAPITALS, CapitalsCapability.REGISTRY.id()));
        }
        return out;
    }

    private static Set<String> questCapabilities(QuestDefinition def, Integration integration) {
        Set<String> out = new TreeSet<>();
        for (Requirement requirement : questRequirements(def)) {
            if (requirement.integration() == integration && requirement.capability() != null) {
                out.add(requirement.capability());
            }
        }
        def.conditions().ifPresent(condition -> conditionCapabilities(condition, integration, false, out));
        return out;
    }

    private static void objectiveRequirements(QuestObjective objective, List<Requirement> out) {
        if (objective instanceof TownsteadObjective townstead) {
            out.add(new Requirement(Integration.TOWNSTEAD, null));
            townstead.requiredCapabilities().forEach(c -> out.add(townstead(c)));
        }
        if (objective instanceof VillagerTargeted targeted) target(targeted.targetSelector(), out);
        if (objective instanceof TradeWithVillagerObjective trade) trade.villager().ifPresent(t -> target(t, out));
        if (objective instanceof EscortEntityObjective escort) anchor(escort.destination(), out);
        if (objective instanceof ReachLocationObjective reach) anchor(reach.location(), out);
        if (objective instanceof BuildNearLocationObjective build) anchor(build.location(), out);
        if (objective instanceof DefendLocationObjective defend) anchor(defend.location(), out);
        if (objective instanceof BreedAnimalsObjective breed) breed.near().ifPresent(a -> anchor(a, out));
        if (objective instanceof TameAnimalObjective tame) tame.near().ifPresent(a -> anchor(a, out));
    }

    private static void rewardRequirements(QuestReward reward, List<Requirement> out) {
        // Townstead rewards are deliberately absent: they are optional side effects (see class javadoc).
        if (reward instanceof CapitalTitleReward) out.add(capitals(CapitalsCapability.TITLE_GRANTS));
        if (reward instanceof CapitalChronicleReward) out.add(capitals(CapitalsCapability.CHRONICLE));
        if (reward instanceof CapitalVillagerTitleReward title) {
            out.add(capitals(CapitalsCapability.VILLAGER_TITLES));
            target(title.villager(), out);
        }
    }

    private static void anchor(LocationAnchor anchor, List<Requirement> out) {
        if (anchor.type() == LocationAnchor.Type.TOWNSTEAD_BUILDING) {
            out.add(townstead(TownsteadCapability.READ_BUILDING));
        }
        anchor.villager().ifPresent(t -> target(t, out));
    }

    private static void target(VillagerTarget target, List<Requirement> out) {
        if (target.mode() == VillagerTarget.Mode.CAPITAL_ROLE) out.add(capitals(CapitalsCapability.ROLES));
    }

    // ------------------------------------------------------------------ projects

    static boolean projectAvailable(ProjectDefinition def, Availability availability) {
        if (!requirementsMet(projectRequirements(def), availability)) {
            return false;
        }
        if (def.conditions().isPresent() && !conditionAvailable(def.conditions().get(), availability, false)) {
            return false;
        }
        for (ProjectPhase phase : def.phases()) {
            if (phase.unlock().isPresent() && !conditionAvailable(phase.unlock().get(), availability, false)) {
                return false;
            }
        }
        return true;
    }

    private static List<Requirement> projectRequirements(ProjectDefinition def) {
        List<Requirement> out = new ArrayList<>();
        for (ProjectPhase phase : def.phases()) {
            for (ProjectObjective objective : phase.objectives()) {
                if (objective instanceof TownsteadProjectObjective townstead) {
                    out.add(new Requirement(Integration.TOWNSTEAD, null));
                    townstead.requiredCapabilities().forEach(c -> out.add(townstead(c)));
                }
            }
            for (SharedReward reward : phase.rewards()) {
                rewardRequirements(reward.reward(), out);
            }
        }
        if (out.stream().anyMatch(r -> r.integration() == Integration.CAPITALS)) {
            out.add(new Requirement(Integration.CAPITALS, CapitalsCapability.REGISTRY.id()));
        }
        return out;
    }

    private static Set<String> projectCapabilities(ProjectDefinition def, Integration integration) {
        Set<String> out = new TreeSet<>();
        for (Requirement requirement : projectRequirements(def)) {
            if (requirement.integration() == integration && requirement.capability() != null) {
                out.add(requirement.capability());
            }
        }
        def.conditions().ifPresent(c -> conditionCapabilities(c, integration, false, out));
        def.phases().forEach(p -> p.unlock().ifPresent(c -> conditionCapabilities(c, integration, false, out)));
        return out;
    }

    // ------------------------------------------------------------------ situations

    static boolean situationAvailable(SituationDefinition def, Availability availability) {
        if (def.trigger() instanceof CapitalInterregnumTrigger
                && !has(availability, Integration.CAPITALS, CapitalsCapability.INTERREGNUM.id())) {
            return false;
        }
        if (def.trigger() instanceof CapitalWarTrigger
                && !has(availability, Integration.CAPITALS, CapitalsCapability.DIPLOMACY.id())) {
            return false;
        }
        if (isTownsteadTrigger(def) && !availability.usable(Integration.TOWNSTEAD)) {
            return false;
        }
        return questAvailable(offerAsQuest(def), availability);
    }

    private static Set<String> situationCapabilities(SituationDefinition def, Integration integration) {
        Set<String> out = new TreeSet<>(questCapabilities(offerAsQuest(def), integration));
        if (integration == Integration.CAPITALS) {
            if (def.trigger() instanceof CapitalInterregnumTrigger) out.add(CapitalsCapability.INTERREGNUM.id());
            if (def.trigger() instanceof CapitalWarTrigger) out.add(CapitalsCapability.DIPLOMACY.id());
        }
        return out;
    }

    /**
     * Situation triggers are matched by type rather than by a shared interface: every Townstead trigger
     * lives in one package under one naming rule, and the check exists to catch a trigger whose
     * situation would otherwise open on a base installation and then wait forever.
     */
    private static boolean isTownsteadTrigger(SituationDefinition def) {
        return def.trigger().getClass().getSimpleName().startsWith("Townstead");
    }

    private static QuestDefinition offerAsQuest(SituationDefinition def) {
        return def.offer().toQuestDefinition(SituationIds.syntheticId(def.id()), def.enabled(), Optional.empty());
    }

    // ------------------------------------------------------------------ requirements and conditions

    /** One flat requirement: an integration, and optionally one capability of it. */
    private record Requirement(Integration integration, String capability) {
    }

    private static Requirement townstead(TownsteadCapability capability) {
        return new Requirement(Integration.TOWNSTEAD, capability.name().toLowerCase(Locale.ROOT));
    }

    private static Requirement capitals(CapitalsCapability capability) {
        return new Requirement(Integration.CAPITALS, capability.id());
    }

    private static boolean requirementsMet(List<Requirement> requirements, Availability availability) {
        for (Requirement requirement : requirements) {
            if (requirement.integration() == Integration.TOWNSTEAD && !availability.usable(Integration.TOWNSTEAD)) {
                return false;
            }
            if (requirement.capability() != null
                    && !has(availability, requirement.integration(), requirement.capability())) {
                return false;
            }
        }
        return true;
    }

    private static boolean has(Availability availability, Integration integration, String capability) {
        return availability.has(integration, capability);
    }

    /**
     * Whether a condition can ever be met on this installation, so far as integrations are concerned.
     * Anything this class does not know about answers {@code true}: whether the player <em>currently</em>
     * satisfies a gate is the offer pipeline's question, not the loader's.
     */
    static boolean conditionAvailable(QuestCondition condition, Availability availability, boolean negated) {
        if (condition instanceof NotCondition not) return conditionAvailable(not.condition(), availability, !negated);
        if (condition instanceof AllOfCondition all) {
            return negated
                    ? all.conditions().stream().anyMatch(c -> conditionAvailable(c, availability, true))
                    : all.conditions().stream().allMatch(c -> conditionAvailable(c, availability, false));
        }
        if (condition instanceof AnyOfCondition any) {
            return negated
                    ? any.conditions().stream().allMatch(c -> conditionAvailable(c, availability, true))
                    : any.conditions().stream().anyMatch(c -> conditionAvailable(c, availability, false));
        }
        if (condition instanceof CompatCapabilityCondition compat) {
            Optional<Integration> integration = Integration.byModId(compat.provider());
            if (integration.isEmpty() || compat.present() == negated) return true;
            return availability.usable(integration.get())
                    && availability.has(integration.get(), normalise(integration.get(), compat.capability()));
        }
        // Townstead: an absent-mod gate (under `not`) is the opposite of a requirement.
        if (condition instanceof TownsteadAvailableCondition available) {
            if (negated) return true;
            if (!availability.usable(Integration.TOWNSTEAD)) return false;
            for (TownsteadCapability capability : available.capabilities()) {
                if (!availability.has(Integration.TOWNSTEAD, capability.name().toLowerCase(Locale.ROOT))) return false;
            }
            return true;
        }
        if (isTownsteadLeaf(condition)) {
            return negated || availability.usable(Integration.TOWNSTEAD);
        }
        // Capitals: the 1.6.0 rules, unchanged — a court gate needs the court whichever way it points.
        if (condition instanceof CapitalPresentCondition) return capital(availability, CapitalsCapability.REGISTRY);
        if (condition instanceof CapitalRoleCondition role) {
            return capital(availability, CapitalsCapability.REGISTRY)
                    && capital(availability, role.subject() == CapitalRoleCondition.Subject.PLAYER
                    ? CapitalsCapability.PLAYER_TITLES : CapitalsCapability.ROLES);
        }
        if (condition instanceof CapitalAllegianceCondition allegiance) {
            return capital(availability, CapitalsCapability.ALLEGIANCE)
                    && (allegiance.match() == CapitalAllegianceCondition.Match.ANY
                    || capital(availability, CapitalsCapability.REGISTRY));
        }
        if (condition instanceof CapitalRelationCondition relation) {
            return capital(availability, CapitalsCapability.REGISTRY) && capital(availability, CapitalsCapability.DIPLOMACY)
                    && (relation.other() != CapitalRelationCondition.Other.ALLEGIANCE
                    || capital(availability, CapitalsCapability.ALLEGIANCE));
        }
        if (condition instanceof CapitalInterregnumCondition) {
            return capital(availability, CapitalsCapability.REGISTRY) && capital(availability, CapitalsCapability.INTERREGNUM);
        }
        return true;
    }

    /** The capabilities a condition tree names for {@code integration} in a mandatory position. */
    private static void conditionCapabilities(QuestCondition condition, Integration integration, boolean negated,
                                              Set<String> out) {
        if (condition instanceof NotCondition not) {
            conditionCapabilities(not.condition(), integration, !negated, out);
        } else if (condition instanceof AllOfCondition all) {
            all.conditions().forEach(c -> conditionCapabilities(c, integration, negated, out));
        } else if (condition instanceof AnyOfCondition any) {
            any.conditions().forEach(c -> conditionCapabilities(c, integration, negated, out));
        } else if (condition instanceof TownsteadAvailableCondition available && !negated
                && integration == Integration.TOWNSTEAD) {
            available.capabilities().forEach(c -> out.add(c.name().toLowerCase(Locale.ROOT)));
        } else if (condition instanceof CompatCapabilityCondition compat && compat.present() != negated
                && Integration.byModId(compat.provider()).filter(i -> i == integration).isPresent()) {
            out.add(normalise(integration, compat.capability()));
        } else if (integration == Integration.CAPITALS) {
            if (condition instanceof CapitalPresentCondition) out.add(CapitalsCapability.REGISTRY.id());
            if (condition instanceof CapitalInterregnumCondition) out.add(CapitalsCapability.INTERREGNUM.id());
            if (condition instanceof CapitalRelationCondition) out.add(CapitalsCapability.DIPLOMACY.id());
            if (condition instanceof CapitalAllegianceCondition) out.add(CapitalsCapability.ALLEGIANCE.id());
            if (condition instanceof CapitalRoleCondition role) {
                out.add(role.subject() == CapitalRoleCondition.Subject.PLAYER
                        ? CapitalsCapability.PLAYER_TITLES.id() : CapitalsCapability.ROLES.id());
            }
        }
    }

    private static boolean isTownsteadLeaf(QuestCondition condition) {
        return condition instanceof TownsteadBuildingCondition
                || condition instanceof TownsteadProfessionTrackCondition
                || condition instanceof TownsteadSkillCondition
                || condition instanceof TownsteadSpiritCondition
                || condition instanceof TownsteadValueCondition;
    }

    private static boolean capital(Availability availability, CapitalsCapability capability) {
        return availability.has(Integration.CAPITALS, capability.id());
    }

    /** Capability ids as the provider spells them: Townstead lowercased names, Capitals dotted ids. */
    private static String normalise(Integration integration, String capability) {
        return switch (integration) {
            case TOWNSTEAD -> capability.toLowerCase(Locale.ROOT);
            case CAPITALS -> capitalsCapability(capability).map(CapitalsCapability::id).orElse(capability);
        };
    }

    private static Optional<TownsteadCapability> townsteadCapability(String id) {
        for (TownsteadCapability capability : TownsteadCapability.values()) {
            if (capability.name().equalsIgnoreCase(id)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }

    private static Optional<CapitalsCapability> capitalsCapability(String id) {
        for (CapitalsCapability capability : CapitalsCapability.values()) {
            if (capability.id().equalsIgnoreCase(id) || capability.name().equalsIgnoreCase(id)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }

    /** Test seam: an availability built from explicit sets. */
    public static Availability availabilityOf(boolean townstead, Set<TownsteadCapability> townsteadCapabilities,
                                              Set<CapitalsCapability> capitalsCapabilities) {
        Set<TownsteadCapability> ts = townsteadCapabilities.isEmpty()
                ? EnumSet.noneOf(TownsteadCapability.class) : EnumSet.copyOf(townsteadCapabilities);
        Set<CapitalsCapability> cs = capitalsCapabilities.isEmpty()
                ? EnumSet.noneOf(CapitalsCapability.class) : EnumSet.copyOf(capitalsCapabilities);
        return new Availability() {
            @Override
            public boolean usable(Integration integration) {
                return integration == Integration.TOWNSTEAD ? townstead : !cs.isEmpty();
            }

            @Override
            public boolean has(Integration integration, String capability) {
                return integration == Integration.TOWNSTEAD
                        ? townstead && townsteadCapability(capability).map(ts::contains).orElse(false)
                        : capitalsCapability(capability).map(cs::contains).orElse(false);
            }
        };
    }
}
