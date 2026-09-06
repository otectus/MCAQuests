package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.compat.CompatStatus;

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
 * Resolves MCA Capitals at <em>runtime</em>, by name, and reports what bound as
 * {@link CapitalsCapability capabilities} rather than as one all-or-nothing switch.
 *
 * <p>This is {@code TownsteadBinding}'s design applied to a fourth optional mod, for the same
 * reason. Capitals is compiled against MCA and statically imports it throughout, so its own method
 * descriptors name MCA types; naming any of those in our bytecode would hard-link MCA: Quests to one
 * MCA package layout. So nothing here is bound by parameter type. Methods are matched on owner,
 * name, arity and staticness, and every handle is adapted to an erased shape whose parameters are all
 * {@link Object}.
 *
 * <h2>Capabilities, not a boolean</h2>
 *
 * <p>Each {@link Member} belongs to exactly one capability. A capability is bound only when every
 * required member it declares bound, so one moved data accessor in a Capitals point release disables
 * exactly the feature that read it — the chronicle reward stops being granted, and the throne quests
 * carry on.
 *
 * <h2>The contract</h2>
 *
 * <p><b>Resolution never throws and never returns null.</b> An unresolved member becomes a constant
 * stub returning its type's default, so {@link ReflectiveCapitalsBridge} needs no guard per call.
 * That matters rather than merely reads well: enumerating a class's methods forces the JVM to resolve
 * their descriptors, so a Capitals built against a different MCA than the installed one throws
 * {@code NoClassDefFoundError} out of {@code getMethods()} itself. Caught per owner, that reads as
 * "nothing bound, every capability absent, here is the list" instead of taking the game down.
 */
public final class CapitalsBinding {

    /** The mod id Forge knows Capitals by, and the namespace its content lives in. */
    public static final String MOD_ID = "mcacapitals";

    /**
     * Capitals' package root, stored <em>dotted</em>, never in internal slash form — that is what lets
     * {@code NoCapitalsStaticLinkTest} byte-scan for slash-form references and treat any hit as a
     * regression, with no exemption for this file.
     */
    public static final String PACKAGE = "com.majesttyx.mcacapitals.";

    /** The class whose presence identifies an installed Capitals. */
    public static final String PROBE_CLASS = "capital.CapitalManager";

    private enum Kind { CLASS, VIRTUAL, STATIC }

    /**
     * One thing MCA: Quests needs from Capitals, named relative to {@link #PACKAGE}.
     * Identity-compared, so the bridge refers to members by constant rather than by a string that
     * could typo.
     */
    public static final class Member {

        private final Kind kind;
        private final String ownerRelative;
        private final String name;
        private final Class<?> returnType;
        private final int arity;
        private final CapitalsCapability capability;
        private final boolean optional;

        private Member(Kind kind, String ownerRelative, String name, Class<?> returnType, int arity,
                       CapitalsCapability capability, boolean optional) {
            this.kind = kind;
            this.ownerRelative = ownerRelative;
            this.name = name;
            this.returnType = returnType;
            this.arity = arity;
            this.capability = capability;
            this.optional = optional;
        }

        /** The capability this member belongs to. Never null: nothing here is core-without-capability. */
        public CapitalsCapability capability() {
            return capability;
        }

        /** The owner, relative to {@link #PACKAGE}. */
        public String ownerRelative() {
            return ownerRelative;
        }

        public String memberName() {
            return name;
        }

        public int arity() {
            return arity;
        }

        /** {@code "class"}, {@code "virtual"} or {@code "static"} — the manifest test's fourth key. */
        public String kindName() {
            return kind.name().toLowerCase(java.util.Locale.ROOT);
        }

        /**
         * True for a member that enriches its capability without being required by it.
         *
         * <p>Every required member in this manifest was read off Capitals' own sources, so its absence
         * is genuine news. An optional member is one whose value is <em>informational</em> or whose
         * fallback is honest — the capability answers correctly without it, just less completely.
         */
        public boolean optional() {
            return optional;
        }

        @Override
        public String toString() {
            return kind == Kind.CLASS ? ownerRelative : ownerRelative + "#" + name + "/" + arity;
        }

        /**
         * The erased handle shape. Every parameter is {@link Object} (including the receiver for a
         * virtual) and {@code asType} does the boxing, so callers pass plain references and a Capitals
         * value crosses without ever being named; only the return type stays faithful, so a primitive
         * stub can be a real {@code false}.
         */
        MethodType erasedType() {
            int params = switch (kind) {
                case VIRTUAL -> arity + 1; // receiver first
                case STATIC -> arity;
                case CLASS -> 0;
            };
            return MethodType.methodType(returnType, Collections.nCopies(params, Object.class));
        }
    }

    private static Member type(String ownerRelative, CapitalsCapability capability) {
        return new Member(Kind.CLASS, ownerRelative, "", Object.class, 0, capability, false);
    }

    private static Member statik(String ownerRelative, String name, Class<?> ret, int arity,
                                 CapitalsCapability capability) {
        return new Member(Kind.STATIC, ownerRelative, name, ret, arity, capability, false);
    }

    /** A {@link Member#optional() best-effort} static; see that method for when this is right. */
    private static Member optionalStatik(String ownerRelative, String name, Class<?> ret, int arity,
                                         CapitalsCapability capability) {
        return new Member(Kind.STATIC, ownerRelative, name, ret, arity, capability, true);
    }

    private static Member virtual(String ownerRelative, String name, Class<?> ret, int arity,
                                  CapitalsCapability capability) {
        return new Member(Kind.VIRTUAL, ownerRelative, name, ret, arity, capability, false);
    }

    private static Member optionalVirtual(String ownerRelative, String name, Class<?> ret, int arity,
                                          CapitalsCapability capability) {
        return new Member(Kind.VIRTUAL, ownerRelative, name, ret, arity, capability, true);
    }

    // ---------------------------------------------------------------------------------------------
    // The manifest — every Capitals class and member MCA: Quests touches.
    //
    // Verified member by member against the Capitals 1.3.6 (Forge 1.20.1) sources. Every entry is
    // unique by (owner, name, arity, staticness) there, so none of them needs a parameter type to
    // disambiguate — which is what keeps MCA's relocated types out of our constant pool.
    //
    // Nothing here appoints a sovereign, founds a capital, declares a war or moves an allegiance:
    // those are Capitals' own ceremonies and its own commands run them. The mutations we perform are
    // three, and each has a chronicle line or a title behind it.
    // ---------------------------------------------------------------------------------------------

    private static final String O_MANAGER = "capital.CapitalManager";
    private static final String O_RECORD = "capital.CapitalRecord";
    private static final String O_NAME_SERVICE = "capital.CapitalNameService";
    private static final String O_AMBASSADOR = "capital.CapitalAmbassadorService";
    private static final String O_CHRONICLE = "capital.CapitalChronicleService";
    private static final String O_DIPLOMATIC_STATE = "capital.CapitalDiplomaticState";
    private static final String O_NOBLE_TITLE = "noble.NobleTitle";
    private static final String O_PLAYER_TITLES = "player.PlayerCapitalTitleService";
    private static final String O_DATA = "data.CapitalDataAccess";
    private static final String O_DIPLOMACY_DATA = "data.CapitalDiplomacyDataAccess";
    private static final String O_INTERREGNUM_DATA = "data.CapitalInterregnumDataAccess";
    private static final String O_INTERREGNUM_RECORD = "data.CapitalInterregnumRecord";
    private static final String O_ALLEGIANCE_DATA = "data.PlayerCapitalAllegianceDataAccess";
    private static final String O_MCA_BRIDGE = "util.MCAIntegrationBridge";

    private static final CapitalsCapability REGISTRY = CapitalsCapability.REGISTRY;
    private static final CapitalsCapability ROLES = CapitalsCapability.ROLES;
    private static final CapitalsCapability PLAYER_TITLES = CapitalsCapability.PLAYER_TITLES;
    private static final CapitalsCapability TITLE_GRANTS = CapitalsCapability.TITLE_GRANTS;
    private static final CapitalsCapability ALLEGIANCE = CapitalsCapability.ALLEGIANCE;
    private static final CapitalsCapability DIPLOMACY = CapitalsCapability.DIPLOMACY;
    private static final CapitalsCapability INTERREGNUM = CapitalsCapability.INTERREGNUM;
    private static final CapitalsCapability CHRONICLE = CapitalsCapability.CHRONICLE;
    private static final CapitalsCapability VILLAGER_TITLES = CapitalsCapability.VILLAGER_TITLES;

    // REGISTRY
    public static final Member CLASS_MANAGER = type(O_MANAGER, REGISTRY);
    public static final Member CLASS_RECORD = type(O_RECORD, REGISTRY);
    public static final Member ALL_CAPITALS = statik(O_MANAGER, "getAllCapitalRecords", Object.class, 0, REGISTRY);
    /** Arity 2 on purpose: the one-argument overload beside it guesses at the dimension. */
    public static final Member CAPITAL_BY_VILLAGE = statik(O_MANAGER, "getCapitalByVillageId", Object.class, 2, REGISTRY);
    public static final Member CAPITAL_BY_ID = statik(O_MANAGER, "getCapital", Object.class, 1, REGISTRY);
    public static final Member CAPITAL_LEVEL = statik(O_MANAGER, "getCapitalLevel", Object.class, 2, REGISTRY);
    public static final Member CAPITAL_OF_RESIDENT = statik(O_MANAGER, "getCapitalForResident", Object.class, 1, REGISTRY);
    public static final Member MARK_DIRTY = statik(O_DATA, "markDirty", void.class, 1, REGISTRY);
    public static final Member REC_CAPITAL_ID = virtual(O_RECORD, "getCapitalId", Object.class, 0, REGISTRY);
    public static final Member REC_VILLAGE_ID = virtual(O_RECORD, "getVillageId", Object.class, 0, REGISTRY);
    public static final Member REC_DIMENSION = virtual(O_RECORD, "getVillageDimensionId", String.class, 0, REGISTRY);
    public static final Member REC_STATE = virtual(O_RECORD, "getState", Object.class, 0, REGISTRY);
    /** Optional: without it a capital is named by its village, which every caller can already do. */
    public static final Member DISPLAY_NAME = optionalStatik(O_NAME_SERVICE, "resolveDisplayName", String.class, 3, REGISTRY);

    // ROLES — single-seat offices, held by a villager.
    public static final Member REC_SOVEREIGN = virtual(O_RECORD, "getSovereign", Object.class, 0, ROLES);
    public static final Member REC_CONSORT = virtual(O_RECORD, "getConsort", Object.class, 0, ROLES);
    public static final Member REC_DOWAGER = virtual(O_RECORD, "getDowager", Object.class, 0, ROLES);
    public static final Member REC_HEIR = virtual(O_RECORD, "getHeir", Object.class, 0, ROLES);
    public static final Member REC_HAND = virtual(O_RECORD, "getHand", Object.class, 0, ROLES);
    public static final Member REC_COMMANDER = virtual(O_RECORD, "getCommander", Object.class, 0, ROLES);
    public static final Member REC_HERALD = virtual(O_RECORD, "getHerald", Object.class, 0, ROLES);
    public static final Member REC_GRAND_MAESTER = virtual(O_RECORD, "getGrandMaester", Object.class, 0, ROLES);
    public static final Member REC_MASTER_OF_LAWS = virtual(O_RECORD, "getMasterOfLaws", Object.class, 0, ROLES);

    // ROLES — set-valued ranks, as a membership test and as the set itself.
    public static final Member REC_IS_ROYAL_CHILD = virtual(O_RECORD, "isRoyalChild", boolean.class, 1, ROLES);
    public static final Member REC_IS_DUKE = virtual(O_RECORD, "isDuke", boolean.class, 1, ROLES);
    public static final Member REC_IS_LORD = virtual(O_RECORD, "isLord", boolean.class, 1, ROLES);
    public static final Member REC_IS_KNIGHT = virtual(O_RECORD, "isKnight", boolean.class, 1, ROLES);
    public static final Member REC_IS_ROYAL_GUARD = virtual(O_RECORD, "isRoyalGuard", boolean.class, 1, ROLES);
    public static final Member REC_ROYAL_CHILDREN = virtual(O_RECORD, "getRoyalChildren", Object.class, 0, ROLES);
    public static final Member REC_DUKES = virtual(O_RECORD, "getDukes", Object.class, 0, ROLES);
    public static final Member REC_LORDS = virtual(O_RECORD, "getLords", Object.class, 0, ROLES);
    public static final Member REC_KNIGHTS = virtual(O_RECORD, "getKnights", Object.class, 0, ROLES);
    public static final Member REC_ROYAL_GUARDS = virtual(O_RECORD, "getRoyalGuards", Object.class, 0, ROLES);

    // ROLES — the fringes of membership. Optional because "member" is still an honest answer without
    // them: they only ever widen the union, never narrow it, so a Capitals that dropped one would
    // leave the role gating slightly stricter rather than wrong.
    public static final Member REC_IS_HOUSEHOLD = optionalVirtual(O_RECORD, "isRoyalHouseholdMember", boolean.class, 1, ROLES);
    public static final Member REC_IS_DISINHERITED = optionalVirtual(O_RECORD, "isDisinheritedRoyalChild", boolean.class, 1, ROLES);
    public static final Member REC_IS_LEGITIMIZED = optionalVirtual(O_RECORD, "isLegitimizedRoyalChild", boolean.class, 1, ROLES);
    public static final Member REC_IS_DISGRACED_GUARD = optionalVirtual(O_RECORD, "isDisgracedRoyalGuard", boolean.class, 1, ROLES);

    /** The ambassador is the one office Capitals keeps off the record, in its diplomacy store. */
    public static final Member AMBASSADOR = statik(O_AMBASSADOR, "getAmbassador", Object.class, 2, ROLES);

    // PLAYER_TITLES. A player throne is a separate pair of fields from the villager sovereign, which
    // is why every sovereign question has to be asked twice.
    public static final Member REC_IS_PLAYER_SOVEREIGN = virtual(O_RECORD, "isPlayerSovereign", boolean.class, 0, PLAYER_TITLES);
    public static final Member REC_IS_PLAYER_CONSORT = virtual(O_RECORD, "isPlayerConsort", boolean.class, 0, PLAYER_TITLES);
    public static final Member REC_PLAYER_SOVEREIGN_ID = virtual(O_RECORD, "getPlayerSovereignId", Object.class, 0, PLAYER_TITLES);
    public static final Member REC_PLAYER_CONSORT_ID = virtual(O_RECORD, "getPlayerConsortId", Object.class, 0, PLAYER_TITLES);
    public static final Member GRANTED_TITLE = statik(O_PLAYER_TITLES, "getGrantedTitle", Object.class, 3, PLAYER_TITLES);
    public static final Member PLAYER_IS_HAND = statik(O_PLAYER_TITLES, "isHand", boolean.class, 3, PLAYER_TITLES);
    public static final Member PLAYER_IS_COMMANDER = statik(O_PLAYER_TITLES, "isCommander", boolean.class, 3, PLAYER_TITLES);

    // TITLE_GRANTS. grantTitle marks its own saved data dirty, so nothing follows it.
    public static final Member CLASS_NOBLE_TITLE = type(O_NOBLE_TITLE, TITLE_GRANTS);
    public static final Member GRANT_TITLE = statik(O_PLAYER_TITLES, "grantTitle", void.class, 4, TITLE_GRANTS);
    /** Optional: an unknown gender grants the masculine constant, which is the documented default. */
    public static final Member PLAYER_IS_FEMALE = optionalStatik(O_MCA_BRIDGE, "isPlayerFemale", boolean.class, 2, TITLE_GRANTS);

    // ALLEGIANCE
    public static final Member DECLARED_CAPITAL = statik(O_ALLEGIANCE_DATA, "getDeclaredCapitalId", Object.class, 2, ALLEGIANCE);

    // DIPLOMACY. The read facade only — getOrCreateRelationship mutates and is never bound.
    public static final Member CLASS_DIPLOMATIC_STATE = type(O_DIPLOMATIC_STATE, DIPLOMACY);
    public static final Member DIPLOMATIC_STATE = statik(O_DIPLOMACY_DATA, "getDiplomaticState", Object.class, 3, DIPLOMACY);

    // INTERREGNUM
    public static final Member INTERREGNUM_RECORD = statik(O_INTERREGNUM_DATA, "getRecord", Object.class, 2, INTERREGNUM);
    public static final Member INTERREGNUM_SNAPSHOT = statik(O_INTERREGNUM_DATA, "getSnapshot", Object.class, 1, INTERREGNUM);
    public static final Member IR_DECEASED = virtual(O_INTERREGNUM_RECORD, "getDeceasedSovereignId", Object.class, 0, INTERREGNUM);
    public static final Member IR_WAS_PLAYER = virtual(O_INTERREGNUM_RECORD, "wasPlayerSovereign", boolean.class, 0, INTERREGNUM);
    /** Optional: only names <em>which</em> player lost the throne, which no gating depends on. */
    public static final Member IR_FORMER_PLAYER = optionalVirtual(O_INTERREGNUM_RECORD, "getFormerPlayerSovereignId", Object.class, 0, INTERREGNUM);

    // CHRONICLE. Both entry points write flat text de-duplicated on the exact string.
    public static final Member CHRONICLE_ENTRY = statik(O_CHRONICLE, "addEntry", void.class, 3, CHRONICLE);
    public static final Member CHRONICLE_ENTRY_QUIET = statik(O_CHRONICLE, "addEntryWithoutHerald", void.class, 2, CHRONICLE);

    // VILLAGER_TITLES. These are the entry points Capitals' own CourtAssignmentService uses;
    // noble.NobleManager is an unpersisted in-memory map that no Capitals title check consults, so
    // binding it would have written titles nothing ever read.
    public static final Member REC_ADD_KNIGHT = virtual(O_RECORD, "addKnight", void.class, 2, VILLAGER_TITLES);
    public static final Member REC_ADD_LORD = virtual(O_RECORD, "addLord", void.class, 2, VILLAGER_TITLES);
    public static final Member REC_ADD_DUKE = virtual(O_RECORD, "addDuke", void.class, 2, VILLAGER_TITLES);
    /** Optional: an unknown gender titles the villager masculine, exactly as the player path does. */
    public static final Member VILLAGER_IS_FEMALE = optionalStatik(O_MCA_BRIDGE, "isFemale", boolean.class, 2, VILLAGER_TITLES);

    /** Every member, in declaration order. The single source of truth for what this mod touches. */
    public static final List<Member> MANIFEST = List.of(
            CLASS_MANAGER, CLASS_RECORD, ALL_CAPITALS, CAPITAL_BY_VILLAGE, CAPITAL_BY_ID,
            CAPITAL_LEVEL, CAPITAL_OF_RESIDENT, MARK_DIRTY, REC_CAPITAL_ID, REC_VILLAGE_ID,
            REC_DIMENSION, REC_STATE, DISPLAY_NAME,
            REC_SOVEREIGN, REC_CONSORT, REC_DOWAGER, REC_HEIR, REC_HAND, REC_COMMANDER, REC_HERALD,
            REC_GRAND_MAESTER, REC_MASTER_OF_LAWS,
            REC_IS_ROYAL_CHILD, REC_IS_DUKE, REC_IS_LORD, REC_IS_KNIGHT, REC_IS_ROYAL_GUARD,
            REC_ROYAL_CHILDREN, REC_DUKES, REC_LORDS, REC_KNIGHTS, REC_ROYAL_GUARDS,
            REC_IS_HOUSEHOLD, REC_IS_DISINHERITED, REC_IS_LEGITIMIZED, REC_IS_DISGRACED_GUARD,
            AMBASSADOR,
            REC_IS_PLAYER_SOVEREIGN, REC_IS_PLAYER_CONSORT, REC_PLAYER_SOVEREIGN_ID,
            REC_PLAYER_CONSORT_ID, GRANTED_TITLE, PLAYER_IS_HAND, PLAYER_IS_COMMANDER,
            CLASS_NOBLE_TITLE, GRANT_TITLE, PLAYER_IS_FEMALE,
            DECLARED_CAPITAL,
            CLASS_DIPLOMATIC_STATE, DIPLOMATIC_STATE,
            INTERREGNUM_RECORD, INTERREGNUM_SNAPSHOT, IR_DECEASED, IR_WAS_PLAYER, IR_FORMER_PLAYER,
            CHRONICLE_ENTRY, CHRONICLE_ENTRY_QUIET,
            REC_ADD_KNIGHT, REC_ADD_LORD, REC_ADD_DUKE, VILLAGER_IS_FEMALE);

    /**
     * The capabilities this manifest covers. Status is measured against these rather than against
     * every {@link CapitalsCapability} constant, so a capability whose members have not been declared
     * yet cannot be mistaken for one that bound.
     */
    public static final Set<CapitalsCapability> DECLARED_CAPABILITIES = declaredCapabilities();

    private static Set<CapitalsCapability> declaredCapabilities() {
        EnumSet<CapitalsCapability> declared = EnumSet.noneOf(CapitalsCapability.class);
        for (Member member : MANIFEST) {
            // Required members only: a capability whose every member were optional could never be
            // found missing, and would report as bound on a Capitals that has none of it.
            if (!member.optional) {
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

        private final CompatStatus status;
        private final Set<CapitalsCapability> capabilities;
        private final Map<Member, MethodHandle> resolved;
        private final Map<Member, Class<?>> types;
        private final List<String> unresolved;

        private Resolution(CompatStatus status, Set<CapitalsCapability> capabilities,
                           Map<Member, MethodHandle> resolved, Map<Member, Class<?>> types,
                           List<String> unresolved) {
            this.status = status;
            this.capabilities = capabilities;
            this.resolved = resolved;
            this.types = types;
            this.unresolved = List.copyOf(unresolved);
        }

        public CompatStatus status() {
            return status;
        }

        /** The capabilities whose every required member bound. */
        public Set<CapitalsCapability> capabilities() {
            return capabilities;
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

        /**
         * The class behind a {@code CLASS} member, or null when it did not load. The bridge uses this
         * for {@link Enum#valueOf} only, so it can name a Capitals enum constant by string and read
         * one back through {@link Enum#name()} without ever naming the enum type.
         */
        @Nullable
        public Class<?> type(Member member) {
            return types.get(member);
        }

        public boolean has(Member member) {
            return resolved.containsKey(member) || types.containsKey(member);
        }

        public boolean has(CapitalsCapability capability) {
            return capabilities.contains(capability);
        }
    }

    /**
     * A resolution in which nothing bound, used when Capitals is not installed, when the integration
     * is switched off, and as the last-ditch value if resolution itself somehow fails. Every handle it
     * hands out is still a working stub.
     */
    public static Resolution absent() {
        return new Resolution(CompatStatus.ABSENT, Set.of(), Map.of(), Map.of(), List.of());
    }

    /**
     * Resolves the whole manifest against {@code loader}. Never throws: every failure is recorded and
     * turned into a stub.
     *
     * <p>A Capitals that is installed but from which nothing at all bound reports
     * {@link CompatStatus#PARTIAL} with every capability absent rather than {@code ABSENT}, because
     * the two are different problems: one is a normal installation, the other is a Capitals build this
     * manifest no longer understands, and only the second is worth a line in the log.
     */
    public static Resolution resolveAgainst(ClassLoader loader) {
        if (loadOrNull(loader, PACKAGE + PROBE_CLASS) == null) {
            return absent();
        }

        Map<Member, MethodHandle> resolved = new IdentityHashMap<>();
        Map<Member, Class<?>> types = new IdentityHashMap<>();
        List<String> unresolved = new ArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Map<String, Method[]> methodCache = new HashMap<>();

        for (Member member : MANIFEST) {
            boolean bound = false;
            try {
                if (member.kind == Kind.CLASS) {
                    Class<?> owner = loadOrNull(loader, PACKAGE + member.ownerRelative);
                    if (owner != null) {
                        types.put(member, owner);
                        bound = true;
                    }
                } else {
                    MethodHandle handle =
                            bindMethod(lookup, methodsOf(loader, methodCache, member.ownerRelative), member);
                    if (handle != null) {
                        resolved.put(member, handle);
                        bound = true;
                    }
                }
            } catch (Throwable ignored) {
                // Recorded below as an ordinary miss; see the javadoc for why this must not escape.
            }
            // An optional member is not news: its capability answers correctly without it, and
            // reporting it would send someone chasing a binding failure that has no symptom.
            if (!bound && !member.optional) {
                unresolved.add(member.toString());
            }
        }

        EnumSet<CapitalsCapability> bound = EnumSet.copyOf(DECLARED_CAPABILITIES);
        for (Member member : MANIFEST) {
            if (!member.optional && !resolved.containsKey(member) && !types.containsKey(member)) {
                bound.remove(member.capability);
            }
        }

        CompatStatus status = bound.size() == DECLARED_CAPABILITIES.size()
                ? CompatStatus.FULL
                : CompatStatus.PARTIAL;

        return new Resolution(status, Collections.unmodifiableSet(bound), resolved, types, unresolved);
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
     * {@code initialize = false} is deliberate: a probe must not run a Capitals class's static
     * initialiser, which would touch MCA and its saved data before Forge is ready for either.
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
     * Every member in the manifest is unique under that key in Capitals 1.3.6.
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

    private CapitalsBinding() {
    }
}
