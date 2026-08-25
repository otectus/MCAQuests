package dev.otectus.mcaquests.compat.townstead;

import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadStatus;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Townstead at <em>runtime</em>, by name, and reports what bound as
 * {@link TownsteadCapability capabilities} rather than as one all-or-nothing switch.
 *
 * <p>This is {@code McaBinding}'s design applied to a second optional mod, and it exists for a
 * sharper reason. Townstead is compiled against MCA, so its own method descriptors name MCA types:
 * {@code TownsteadAPI.villager(VillagerEntityMCA)}, {@code VillageSpiritAggregator.totalsFor(Village)}.
 * <b>Naming any of those in our bytecode would hard-link us to one MCA package layout</b> — the exact
 * failure that killed dedicated servers in 1.3.0. So nothing here is bound by parameter type. Methods
 * are matched on owner, name, arity and staticness, and every handle is adapted to an erased shape
 * whose parameters are all {@link Object}; the MCA value simply passes through as a reference we
 * never name.
 *
 * <h2>Capabilities, not a boolean</h2>
 *
 * <p>Each {@link Member} belongs to one capability, or to none at all — the handful of "core" members
 * without which there is no facade to speak of. A capability is bound only when every member it
 * declares bound, so one moved internal method in a Townstead point release disables exactly the
 * feature that needed it. Missing a core member is {@link TownsteadStatus#DISABLED}; missing some
 * capabilities is {@link TownsteadStatus#PARTIAL}.
 *
 * <h2>The contract</h2>
 *
 * <p><b>Resolution never throws and never returns null.</b> An unresolved member becomes a constant
 * stub returning its type's default, so call sites in {@link TownsteadHandles} need no guards. That
 * matters more than convenience here: enumerating a class's methods forces the JVM to resolve their
 * descriptors, so a Townstead built against a different MCA layout than the installed one throws
 * {@code NoClassDefFoundError} from {@code getMethods()} itself. Caught per owner, that mismatch
 * reads as "nothing bound, status DISABLED, here is the version" instead of taking the game down.
 *
 * @see TownsteadHandles for the resolved handles themselves
 */
public final class TownsteadBinding {

    /**
     * Townstead's package root, stored <em>dotted</em>, never in internal slash form — that is what
     * lets {@code NoTownsteadStaticLinkTest} byte-scan for slash-form references and treat any hit as
     * a regression, with no exemption for this file.
     */
    private static final String PACKAGE = "com.aetherianartificer.townstead.";

    /** The class whose presence identifies an installed, API-bearing Townstead. */
    private static final String PROBE_CLASS = "api.TownsteadAPI";

    /**
     * A method whose first parameter is an MCA villager. Its parameter type's <em>runtime</em> name
     * tells us which MCA package layout this Townstead was compiled against — read reflectively as a
     * string, so it never becomes linkage. Diagnostics only: no code path ever branches on it.
     */
    private static final String VARIANT_PROBE_METHOD = "villager";

    private enum Kind { CLASS, VIRTUAL, STATIC }

    /**
     * One thing MCA: Quests needs from Townstead, named relative to {@link #PACKAGE}.
     * Identity-compared, so {@link TownsteadHandles} refers to members by constant rather than by a
     * string that could typo.
     */
    public static final class Member {

        private final Kind kind;
        private final String ownerRelative;
        private final String name;
        private final Class<?> returnType;
        private final int arity;
        @Nullable
        private final TownsteadCapability capability;

        private Member(Kind kind, String ownerRelative, String name, Class<?> returnType, int arity,
                       @Nullable TownsteadCapability capability) {
            this.kind = kind;
            this.ownerRelative = ownerRelative;
            this.name = name;
            this.returnType = returnType;
            this.arity = arity;
            this.capability = capability;
        }

        /** The capability this member belongs to, or {@code null} for a core facade member. */
        @Nullable
        public TownsteadCapability capability() {
            return capability;
        }

        @Override
        public String toString() {
            return kind == Kind.CLASS ? ownerRelative : ownerRelative + "#" + name + "/" + arity;
        }

        /**
         * The erased handle shape. Every parameter is {@link Object} (including the receiver for a
         * virtual) and {@code asType} does the boxing, so callers pass plain references and an MCA
         * argument crosses without ever being named; only the return type stays faithful, so a
         * primitive stub can be a real {@code 0}/{@code false}.
         */
        private MethodType erasedType() {
            int params = switch (kind) {
                case VIRTUAL -> arity + 1; // receiver first
                case STATIC -> arity;
                case CLASS -> 0;
            };
            return MethodType.methodType(returnType, Collections.nCopies(params, Object.class));
        }
    }

    private static Member statik(String ownerRelative, String name, Class<?> ret, int arity,
                                 @Nullable TownsteadCapability capability) {
        return new Member(Kind.STATIC, ownerRelative, name, ret, arity, capability);
    }

    /** A zero-argument accessor — every Townstead snapshot is a record, so this covers nearly all of them. */
    private static Member get(String ownerRelative, String name, Class<?> ret,
                              TownsteadCapability capability) {
        return new Member(Kind.VIRTUAL, ownerRelative, name, ret, 0, capability);
    }

    // ---------------------------------------------------------------------------------------------
    // The manifest — every Townstead class and member MCA: Quests reads.
    //
    // Verified member-by-member against townstead-0.7.6+1.20.1.jar. Every entry is unique by
    // (owner, name, arity, staticness) in that jar, so none of them needs a parameter type to
    // disambiguate — which is what keeps MCA's relocated types out of our constant pool.
    //
    // Mutations (needs, profession XP, skills, reactions) are deliberately absent: they are declared
    // in a later milestone, so a capability whose members do not exist yet cannot report as bound.
    // ---------------------------------------------------------------------------------------------

    private static final String O_API = "api.TownsteadAPI";
    private static final String O_VILLAGER = "api.TownsteadVillagerSnapshot";
    private static final String O_NEEDS = "api.TownsteadNeedsSnapshot";
    private static final String O_SCHEDULE = "api.TownsteadScheduleSnapshot";
    private static final String O_CALENDAR = "api.TownsteadCalendarSnapshot";
    private static final String O_BUILDING = "api.TownsteadBuildingSnapshot";
    private static final String O_ROOT = "api.TownsteadRootSnapshot";
    private static final String O_LIFE_STAGE = "api.TownsteadLifeStageSnapshot";
    private static final String O_GENE = "api.TownsteadGeneSnapshot";
    private static final String O_GENE_VARIANT = "api.TownsteadGeneVariantSnapshot";
    private static final String O_SPIRIT_AGG = "spirit.VillageSpiritAggregator";
    private static final String O_SPIRIT_TOTALS = "spirit.SpiritTotals";
    private static final String O_SPIRIT_READOUT = "spirit.SpiritReadout";
    private static final String O_SPIRIT_REGISTRY = "spirit.SpiritRegistry";

    private static final TownsteadCapability CAP_VILLAGER = TownsteadCapability.READ_VILLAGER;
    private static final TownsteadCapability CAP_PROFESSION = TownsteadCapability.READ_PROFESSION;
    private static final TownsteadCapability CAP_NEEDS = TownsteadCapability.READ_NEEDS;
    private static final TownsteadCapability CAP_SCHEDULE = TownsteadCapability.READ_SCHEDULE;
    private static final TownsteadCapability CAP_CALENDAR = TownsteadCapability.READ_CALENDAR;
    private static final TownsteadCapability CAP_BUILDING = TownsteadCapability.READ_BUILDING;
    private static final TownsteadCapability CAP_ROOT = TownsteadCapability.READ_ROOT;
    private static final TownsteadCapability CAP_GENE = TownsteadCapability.READ_GENE;
    private static final TownsteadCapability CAP_SPIRIT = TownsteadCapability.READ_SPIRIT;

    // READ_VILLAGER. entity(Entity) is the safe entry point: its parameter descriptor is vanilla-only,
    // unlike the villager(VillagerEntityMCA) overload beside it, which must never be bound.
    public static final Member API_ENTITY = statik(O_API, "entity", Object.class, 1, CAP_VILLAGER);
    public static final Member V_UUID = get(O_VILLAGER, "uuid", Object.class, CAP_VILLAGER);
    public static final Member V_NAME = get(O_VILLAGER, "name", Object.class, CAP_VILLAGER);
    public static final Member V_ENTITY_TYPE = get(O_VILLAGER, "entityType", Object.class, CAP_VILLAGER);
    public static final Member V_ROOT_ID = get(O_VILLAGER, "rootId", Object.class, CAP_VILLAGER);
    public static final Member V_LIFE_STAGE = get(O_VILLAGER, "lifeStage", Object.class, CAP_VILLAGER);
    public static final Member V_AGE_DAYS = get(O_VILLAGER, "biologicalAgeDays", long.class, CAP_VILLAGER);
    public static final Member V_AGE_YEARS = get(O_VILLAGER, "apparentAgeYears", int.class, CAP_VILLAGER);
    public static final Member V_IMMORTAL = get(O_VILLAGER, "immortal", boolean.class, CAP_VILLAGER);
    public static final Member V_AGELESS = get(O_VILLAGER, "ageless", boolean.class, CAP_VILLAGER);
    public static final Member V_SENIOR = get(O_VILLAGER, "senior", boolean.class, CAP_VILLAGER);
    public static final Member V_PERSONALITY = get(O_VILLAGER, "personalityId", Object.class, CAP_VILLAGER);
    public static final Member V_FERTILITY = get(O_VILLAGER, "fertility", float.class, CAP_VILLAGER);
    public static final Member V_CARRIED = get(O_VILLAGER, "carriedVariants", Object.class, CAP_VILLAGER);
    public static final Member V_ALLELES = get(O_VILLAGER, "expressedAlleles", Object.class, CAP_VILLAGER);
    public static final Member V_HERITAGE = get(O_VILLAGER, "heritage", Object.class, CAP_VILLAGER);

    // READ_PROFESSION
    public static final Member V_PROFESSION_ID = get(O_VILLAGER, "professionId", Object.class, CAP_PROFESSION);
    public static final Member V_PROFESSION_LEVEL = get(O_VILLAGER, "professionLevel", int.class, CAP_PROFESSION);
    public static final Member V_PROFESSION_XP = get(O_VILLAGER, "professionXp", int.class, CAP_PROFESSION);

    // READ_NEEDS
    public static final Member V_NEEDS = get(O_VILLAGER, "needs", Object.class, CAP_NEEDS);
    public static final Member N_HUNGER = get(O_NEEDS, "hunger", int.class, CAP_NEEDS);
    public static final Member N_SATURATION = get(O_NEEDS, "saturation", float.class, CAP_NEEDS);
    public static final Member N_HUNGER_EXHAUSTION = get(O_NEEDS, "hungerExhaustion", float.class, CAP_NEEDS);
    public static final Member N_THIRST = get(O_NEEDS, "thirst", int.class, CAP_NEEDS);
    public static final Member N_QUENCHED = get(O_NEEDS, "quenched", int.class, CAP_NEEDS);
    public static final Member N_THIRST_EXHAUSTION = get(O_NEEDS, "thirstExhaustion", float.class, CAP_NEEDS);
    public static final Member N_FATIGUE = get(O_NEEDS, "fatigue", int.class, CAP_NEEDS);
    public static final Member N_COLLAPSED = get(O_NEEDS, "collapsed", boolean.class, CAP_NEEDS);
    public static final Member N_GATED = get(O_NEEDS, "gated", boolean.class, CAP_NEEDS);

    // READ_SCHEDULE
    public static final Member V_SCHEDULE = get(O_VILLAGER, "schedule", Object.class, CAP_SCHEDULE);
    public static final Member S_MODE = get(O_SCHEDULE, "mode", Object.class, CAP_SCHEDULE);
    public static final Member S_TEMPLATE_ID = get(O_SCHEDULE, "templateId", Object.class, CAP_SCHEDULE);
    public static final Member S_CUSTOM_SHIFTS = get(O_SCHEDULE, "customShifts", boolean.class, CAP_SCHEDULE);
    public static final Member S_NON_DEFAULT_SHIFTS = get(O_SCHEDULE, "nonDefaultCustomShifts", boolean.class, CAP_SCHEDULE);
    public static final Member S_TICK_HOUR = get(O_SCHEDULE, "currentTickHour", int.class, CAP_SCHEDULE);
    public static final Member S_DISPLAY_HOUR = get(O_SCHEDULE, "currentDisplayHour", int.class, CAP_SCHEDULE);
    public static final Member S_SHIFT_ORDINAL = get(O_SCHEDULE, "currentShiftOrdinal", int.class, CAP_SCHEDULE);
    public static final Member S_CURRENT_ACTIVITY = get(O_SCHEDULE, "currentActivity", Object.class, CAP_SCHEDULE);
    public static final Member S_PLANNED_ACTIVITY = get(O_SCHEDULE, "plannedActivity", Object.class, CAP_SCHEDULE);
    public static final Member S_CURRENT_TEMPLATE = get(O_SCHEDULE, "currentTemplateId", Object.class, CAP_SCHEDULE);
    public static final Member S_SHIFTS = get(O_SCHEDULE, "shifts", Object.class, CAP_SCHEDULE);
    public static final Member S_WEEKDAY_TEMPLATES = get(O_SCHEDULE, "weekDayTemplates", Object.class, CAP_SCHEDULE);

    // READ_CALENDAR
    public static final Member API_CALENDAR = statik(O_API, "calendar", Object.class, 1, CAP_CALENDAR);
    public static final Member K_PROFILE_ID = get(O_CALENDAR, "profileId", Object.class, CAP_CALENDAR);
    public static final Member K_WORLD_DAY = get(O_CALENDAR, "worldDay", long.class, CAP_CALENDAR);
    public static final Member K_EPOCH_OFFSET = get(O_CALENDAR, "epochYearOffset", int.class, CAP_CALENDAR);
    public static final Member K_TIME_MODE = get(O_CALENDAR, "timeMode", Object.class, CAP_CALENDAR);
    public static final Member K_YEAR = get(O_CALENDAR, "year", int.class, CAP_CALENDAR);
    public static final Member K_MONTH = get(O_CALENDAR, "month", int.class, CAP_CALENDAR);
    public static final Member K_DAY = get(O_CALENDAR, "day", int.class, CAP_CALENDAR);
    public static final Member K_DAY_OF_YEAR = get(O_CALENDAR, "dayOfYear", int.class, CAP_CALENDAR);
    public static final Member K_DAY_OF_WEEK = get(O_CALENDAR, "dayOfWeek", int.class, CAP_CALENDAR);
    public static final Member K_SEASON = get(O_CALENDAR, "season", Object.class, CAP_CALENDAR);

    // READ_BUILDING
    public static final Member API_BUILDING_AT = statik(O_API, "buildingAt", Object.class, 2, CAP_BUILDING);
    public static final Member B_ID = get(O_BUILDING, "id", int.class, CAP_BUILDING);
    public static final Member B_VILLAGE_ID = get(O_BUILDING, "villageId", int.class, CAP_BUILDING);
    public static final Member B_TYPE = get(O_BUILDING, "type", Object.class, CAP_BUILDING);
    public static final Member B_SIZE = get(O_BUILDING, "size", int.class, CAP_BUILDING);
    public static final Member B_CENTER_X = get(O_BUILDING, "centerX", int.class, CAP_BUILDING);
    public static final Member B_CENTER_Y = get(O_BUILDING, "centerY", int.class, CAP_BUILDING);
    public static final Member B_CENTER_Z = get(O_BUILDING, "centerZ", int.class, CAP_BUILDING);
    public static final Member B_MIN_X = get(O_BUILDING, "minX", int.class, CAP_BUILDING);
    public static final Member B_MIN_Y = get(O_BUILDING, "minY", int.class, CAP_BUILDING);
    public static final Member B_MIN_Z = get(O_BUILDING, "minZ", int.class, CAP_BUILDING);
    public static final Member B_MAX_X = get(O_BUILDING, "maxX", int.class, CAP_BUILDING);
    public static final Member B_MAX_Y = get(O_BUILDING, "maxY", int.class, CAP_BUILDING);
    public static final Member B_MAX_Z = get(O_BUILDING, "maxZ", int.class, CAP_BUILDING);

    // READ_ROOT
    public static final Member API_ORIGIN = statik(O_API, "origin", Object.class, 1, CAP_ROOT);
    public static final Member R_ID = get(O_ROOT, "id", Object.class, CAP_ROOT);
    public static final Member R_DISPLAY_NAME = get(O_ROOT, "displayName", Object.class, CAP_ROOT);
    public static final Member R_SPECIES = get(O_ROOT, "species", Object.class, CAP_ROOT);
    public static final Member R_ANCESTRY = get(O_ROOT, "ancestry", Object.class, CAP_ROOT);
    public static final Member R_LINEAGE = get(O_ROOT, "lineage", Object.class, CAP_ROOT);
    public static final Member R_EFFECTIVE_SPECIES = get(O_ROOT, "effectiveSpecies", Object.class, CAP_ROOT);
    public static final Member R_DEFAULT_GENES = get(O_ROOT, "defaultGenes", Object.class, CAP_ROOT);
    public static final Member R_LIFE_STAGES = get(O_ROOT, "lifeStages", Object.class, CAP_ROOT);
    public static final Member LS_ID = get(O_LIFE_STAGE, "id", Object.class, CAP_ROOT);
    public static final Member LS_LABEL = get(O_LIFE_STAGE, "label", Object.class, CAP_ROOT);
    public static final Member LS_DAYS = get(O_LIFE_STAGE, "days", int.class, CAP_ROOT);
    public static final Member LS_SCALE = get(O_LIFE_STAGE, "scale", float.class, CAP_ROOT);
    public static final Member LS_PRESENTS_AS = get(O_LIFE_STAGE, "presentsAs", Object.class, CAP_ROOT);
    public static final Member LS_NARRATIVE_START = get(O_LIFE_STAGE, "narrativeStart", float.class, CAP_ROOT);
    public static final Member LS_NARRATIVE_END = get(O_LIFE_STAGE, "narrativeEnd", float.class, CAP_ROOT);

    // READ_GENE
    public static final Member API_GENE = statik(O_API, "gene", Object.class, 1, CAP_GENE);
    public static final Member G_ID = get(O_GENE, "id", Object.class, CAP_GENE);
    public static final Member G_DISPLAY_NAME = get(O_GENE, "displayName", Object.class, CAP_GENE);
    public static final Member G_DESCRIPTION = get(O_GENE, "description", Object.class, CAP_GENE);
    public static final Member G_CATEGORY = get(O_GENE, "category", Object.class, CAP_GENE);
    public static final Member G_DOMINANCE = get(O_GENE, "dominance", Object.class, CAP_GENE);
    public static final Member G_LOCUS = get(O_GENE, "locus", Object.class, CAP_GENE);
    public static final Member G_WEIGHT = get(O_GENE, "weight", int.class, CAP_GENE);
    public static final Member G_DISPLAY_MODE = get(O_GENE, "displayMode", Object.class, CAP_GENE);
    public static final Member G_VARIANTS = get(O_GENE, "variants", Object.class, CAP_GENE);
    public static final Member GV_ID = get(O_GENE_VARIANT, "id", Object.class, CAP_GENE);
    public static final Member GV_DISPLAY_NAME = get(O_GENE_VARIANT, "displayName", Object.class, CAP_GENE);
    public static final Member GV_WEIGHT = get(O_GENE_VARIANT, "weight", int.class, CAP_GENE);
    public static final Member GV_TYPE = get(O_GENE_VARIANT, "type", Object.class, CAP_GENE);

    // READ_SPIRIT. The only read that reaches past TownsteadAPI, because the public facade has no
    // spirit accessor at all. totalsFor takes MCA's Village; that object comes from McaHandles and
    // passes straight through this erased handle, so the type is never named on either side.
    public static final Member SPIRIT_TOTALS_FOR = statik(O_SPIRIT_AGG, "totalsFor", Object.class, 1, CAP_SPIRIT);
    public static final Member SPIRIT_READOUT_FOR = statik(O_SPIRIT_AGG, "readoutFor", Object.class, 1, CAP_SPIRIT);
    public static final Member SPIRIT_TIER_FOR = statik(O_SPIRIT_AGG, "tierForSpirit", int.class, 1, CAP_SPIRIT);
    public static final Member ST_PER_SPIRIT = get(O_SPIRIT_TOTALS, "perSpirit", Object.class, CAP_SPIRIT);
    public static final Member ST_TOTAL = get(O_SPIRIT_TOTALS, "total", int.class, CAP_SPIRIT);
    public static final Member ST_CONTRIBUTING = get(O_SPIRIT_TOTALS, "contributingBuildings", int.class, CAP_SPIRIT);
    public static final Member SR_CLASSIFICATION = get(O_SPIRIT_READOUT, "classification", Object.class, CAP_SPIRIT);
    public static final Member SR_TIER_INDEX = get(O_SPIRIT_READOUT, "tierIndex", int.class, CAP_SPIRIT);
    public static final Member SR_PRIMARY = get(O_SPIRIT_READOUT, "primarySpiritId", Object.class, CAP_SPIRIT);
    public static final Member SR_SECONDARY = get(O_SPIRIT_READOUT, "secondarySpiritId", Object.class, CAP_SPIRIT);
    public static final Member SPIRIT_CONTAINS = statik(O_SPIRIT_REGISTRY, "contains", boolean.class, 1, CAP_SPIRIT);

    /** Every member, in declaration order. The single source of truth for what this mod reads. */
    public static final List<Member> MANIFEST = List.of(
            API_ENTITY, V_UUID, V_NAME, V_ENTITY_TYPE, V_ROOT_ID, V_LIFE_STAGE, V_AGE_DAYS, V_AGE_YEARS,
            V_IMMORTAL, V_AGELESS, V_SENIOR, V_PERSONALITY, V_FERTILITY, V_CARRIED, V_ALLELES, V_HERITAGE,
            V_PROFESSION_ID, V_PROFESSION_LEVEL, V_PROFESSION_XP,
            V_NEEDS, N_HUNGER, N_SATURATION, N_HUNGER_EXHAUSTION, N_THIRST, N_QUENCHED,
            N_THIRST_EXHAUSTION, N_FATIGUE, N_COLLAPSED, N_GATED,
            V_SCHEDULE, S_MODE, S_TEMPLATE_ID, S_CUSTOM_SHIFTS, S_NON_DEFAULT_SHIFTS, S_TICK_HOUR,
            S_DISPLAY_HOUR, S_SHIFT_ORDINAL, S_CURRENT_ACTIVITY, S_PLANNED_ACTIVITY, S_CURRENT_TEMPLATE,
            S_SHIFTS, S_WEEKDAY_TEMPLATES,
            API_CALENDAR, K_PROFILE_ID, K_WORLD_DAY, K_EPOCH_OFFSET, K_TIME_MODE, K_YEAR, K_MONTH,
            K_DAY, K_DAY_OF_YEAR, K_DAY_OF_WEEK, K_SEASON,
            API_BUILDING_AT, B_ID, B_VILLAGE_ID, B_TYPE, B_SIZE, B_CENTER_X, B_CENTER_Y, B_CENTER_Z,
            B_MIN_X, B_MIN_Y, B_MIN_Z, B_MAX_X, B_MAX_Y, B_MAX_Z,
            API_ORIGIN, R_ID, R_DISPLAY_NAME, R_SPECIES, R_ANCESTRY, R_LINEAGE, R_EFFECTIVE_SPECIES,
            R_DEFAULT_GENES, R_LIFE_STAGES, LS_ID, LS_LABEL, LS_DAYS, LS_SCALE, LS_PRESENTS_AS,
            LS_NARRATIVE_START, LS_NARRATIVE_END,
            API_GENE, G_ID, G_DISPLAY_NAME, G_DESCRIPTION, G_CATEGORY, G_DOMINANCE, G_LOCUS, G_WEIGHT,
            G_DISPLAY_MODE, G_VARIANTS, GV_ID, GV_DISPLAY_NAME, GV_WEIGHT, GV_TYPE,
            SPIRIT_TOTALS_FOR, SPIRIT_READOUT_FOR, SPIRIT_TIER_FOR, ST_PER_SPIRIT, ST_TOTAL,
            ST_CONTRIBUTING, SR_CLASSIFICATION, SR_TIER_INDEX, SR_PRIMARY, SR_SECONDARY, SPIRIT_CONTAINS);

    /**
     * The capabilities this manifest covers. Status is measured against these rather than against
     * every {@link TownsteadCapability} constant, so a capability whose members have not been declared
     * yet cannot be mistaken for one that bound.
     */
    public static final Set<TownsteadCapability> DECLARED_CAPABILITIES = declaredCapabilities();

    private static Set<TownsteadCapability> declaredCapabilities() {
        EnumSet<TownsteadCapability> declared = EnumSet.noneOf(TownsteadCapability.class);
        for (Member member : MANIFEST) {
            if (member.capability != null) {
                declared.add(member.capability);
            }
        }
        return Collections.unmodifiableSet(declared);
    }

    // ---------------------------------------------------------------------------------------------
    // Resolution
    // ---------------------------------------------------------------------------------------------

    /** The outcome of resolving {@link #MANIFEST} against one classloader. Immutable. */
    public static final class Resolution {

        private final TownsteadStatus status;
        private final Set<TownsteadCapability> capabilities;
        private final String variant;
        private final Map<Member, MethodHandle> resolved;
        private final List<String> unresolved;

        private Resolution(TownsteadStatus status, Set<TownsteadCapability> capabilities,
                           @Nullable String variant, Map<Member, MethodHandle> resolved,
                           List<String> unresolved) {
            this.status = status;
            this.capabilities = capabilities;
            this.variant = variant;
            this.resolved = resolved;
            this.unresolved = List.copyOf(unresolved);
        }

        public TownsteadStatus status() {
            return status;
        }

        /** The capabilities whose every declared member bound. */
        public Set<TownsteadCapability> capabilities() {
            return capabilities;
        }

        /**
         * The MCA package root the installed Townstead was compiled against, read reflectively from a
         * method's parameter type at bind time. Diagnostics only.
         */
        @Nullable
        public String variant() {
            return variant;
        }

        /** Members that did not bind, for the status command and the one WARN at startup. */
        public List<String> unresolved() {
            return unresolved;
        }

        /**
         * The handle for a member. <b>Never null</b> — an unresolved member yields a constant stub of
         * the same erased type returning that type's default, so call sites need no guard of their own.
         */
        public MethodHandle handle(Member member) {
            MethodHandle handle = resolved.get(member);
            return handle != null ? handle : MethodHandles.empty(member.erasedType());
        }

        public boolean has(Member member) {
            return resolved.containsKey(member);
        }

        public boolean has(TownsteadCapability capability) {
            return capabilities.contains(capability);
        }
    }

    /**
     * A resolution in which nothing bound, used when Townstead is not installed and as the last-ditch
     * value if resolution itself somehow fails. Every handle it hands out is still a working stub.
     */
    public static Resolution absent() {
        return new Resolution(TownsteadStatus.ABSENT, Set.of(), null, Map.of(), List.of());
    }

    /**
     * Resolves the whole manifest against {@code loader}. Never throws: every failure is recorded and
     * turned into a stub. That is load-bearing rather than tidy — enumerating a class's methods forces
     * the JVM to resolve their parameter descriptors, so a Townstead compiled against a different MCA
     * layout than the installed one throws {@code NoClassDefFoundError} out of {@code getMethods()}.
     * Caught here, that mismatch becomes "nothing bound, status DISABLED" plus one actionable log line.
     */
    public static Resolution resolveAgainst(ClassLoader loader) {
        if (loadOrNull(loader, PACKAGE + PROBE_CLASS) == null) {
            return absent();
        }

        Map<Member, MethodHandle> resolved = new IdentityHashMap<>();
        List<String> unresolved = new ArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Map<String, Method[]> methodCache = new HashMap<>();

        for (Member member : MANIFEST) {
            MethodHandle handle = null;
            try {
                handle = bindMethod(lookup, methodsOf(loader, methodCache, member.ownerRelative), member);
            } catch (Throwable ignored) {
                // Recorded below as an ordinary miss; see the javadoc for why this must not escape.
            }
            if (handle == null) {
                unresolved.add(member.toString());
            } else {
                resolved.put(member, handle);
            }
        }

        EnumSet<TownsteadCapability> bound = EnumSet.copyOf(DECLARED_CAPABILITIES);
        for (Member member : MANIFEST) {
            if (member.capability != null && !resolved.containsKey(member)) {
                bound.remove(member.capability);
            }
        }

        TownsteadStatus status;
        if (bound.isEmpty()) {
            status = TownsteadStatus.DISABLED;
        } else if (bound.size() == DECLARED_CAPABILITIES.size()) {
            status = TownsteadStatus.FULL;
        } else {
            status = TownsteadStatus.PARTIAL;
        }

        return new Resolution(status, Collections.unmodifiableSet(bound),
                probeVariant(methodCache.get(O_API)), resolved, unresolved);
    }

    /**
     * Every public method of an owner, resolved once. Cached because a miss here is a whole-class
     * failure and should be reported identically for each of that class's members, and because
     * {@code getMethods()} is the expensive part of binding.
     */
    private static Method[] methodsOf(ClassLoader loader, Map<String, Method[]> cache, String ownerRelative) {
        return cache.computeIfAbsent(ownerRelative, relative -> {
            Class<?> owner = loadOrNull(loader, PACKAGE + relative);
            if (owner == null) {
                return new Method[0];
            }
            try {
                return owner.getMethods();
            } catch (Throwable t) {
                return new Method[0];
            }
        });
    }

    /**
     * Which MCA package root Townstead was built against, taken from the runtime name of a parameter
     * type rather than from anything we compile against. Returns e.g. {@code "forge.net.mca"}.
     * {@code null} when it cannot be determined, which is not an error — nothing branches on this.
     */
    @Nullable
    private static String probeVariant(@Nullable Method[] apiMethods) {
        if (apiMethods == null) {
            return null;
        }
        for (Method candidate : apiMethods) {
            if (!candidate.getName().equals(VARIANT_PROBE_METHOD) || candidate.getParameterCount() != 1) {
                continue;
            }
            try {
                String parameter = candidate.getParameterTypes()[0].getName();
                int entity = parameter.indexOf(".entity.");
                return entity > 0 ? parameter.substring(0, entity) : parameter;
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /**
     * {@code initialize = false} is deliberate: a probe must not run a Townstead class's static
     * initialiser, which would register content and touch MCA before Forge is ready for it.
     */
    @Nullable
    private static Class<?> loadOrNull(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Finds a method by name, arity and staticness — <b>never by parameter type</b>, which would mean
     * naming MCA's relocated classes and reintroducing the linkage this whole layer exists to avoid.
     * Every member in the manifest is unique under that key in Townstead 0.7.6.
     */
    @Nullable
    private static MethodHandle bindMethod(MethodHandles.Lookup lookup, Method[] candidates, Member member) {
        for (Method candidate : candidates) {
            if (!candidate.getName().equals(member.name)
                    || candidate.getParameterCount() != member.arity
                    || Modifier.isStatic(candidate.getModifiers()) != (member.kind == Kind.STATIC)) {
                continue;
            }
            try {
                candidate.setAccessible(true);
                return lookup.unreflect(candidate).asType(member.erasedType());
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }


    private TownsteadBinding() {
    }
}
