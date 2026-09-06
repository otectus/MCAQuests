package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real {@link CapitalsBridge}, over a resolved {@link CapitalsBinding.Resolution}.
 *
 * <p>Every Capitals value crosses this class as an {@link Object} and leaves it as a UUID, a string
 * or a boolean. Enum constants are read back through {@link Enum#name()} and written by
 * {@link Enum#valueOf}, so no Capitals enum is ever named in our bytecode — including the state names
 * this class compares against, which are plain strings.
 *
 * <p><b>Nothing here throws.</b> A handle that fails is reported once per member at debug and the
 * call answers as if the capability were absent. That is not defensiveness for its own sake: these
 * are called from offer filters, a tick poller and reward grants, where an exception costs more than
 * the answer is worth.
 */
public final class ReflectiveCapitalsBridge implements CapitalsBridge {

    /** The two Capitals states a live court needs. Compared by name; never linked. */
    private static final String STATE_ACTIVE = "ACTIVE";

    private final CapitalsBinding.Resolution resolution;

    /** Members already reported as failing, so a broken handle logs once rather than once a tick. */
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public ReflectiveCapitalsBridge(CapitalsBinding.Resolution resolution) {
        this.resolution = resolution;
    }

    /** A bridge over a freshly resolved manifest. */
    public static ReflectiveCapitalsBridge of(CapitalsBinding.Resolution resolution) {
        return new ReflectiveCapitalsBridge(resolution);
    }

    @Override
    public CompatStatus status() {
        return resolution.status();
    }

    @Override
    public List<CompatCapability> capabilities() {
        List<CompatCapability> capabilities = new ArrayList<>();
        for (CapitalsCapability capability : CapitalsCapability.values()) {
            capabilities.add(new CompatCapability(capability.id(), has(capability),
                    CapabilityEvidence.ADAPTER_CONFIRMED));
        }
        return List.copyOf(capabilities);
    }

    @Override
    public boolean has(CapitalsCapability capability) {
        return resolution.has(capability);
    }

    @Override
    public List<String> unresolvedMembers() {
        return resolution.unresolved();
    }

    // --- the registry ----------------------------------------------------------------------------

    @Override
    public Optional<CapitalRef> capitalForVillage(ServerLevel level, int villageId) {
        if (level == null || !has(CapitalsCapability.REGISTRY)) {
            return Optional.empty();
        }
        return wrap(call(CapitalsBinding.CAPITAL_BY_VILLAGE, level, villageId));
    }

    @Override
    public List<CapitalRef> allCapitals() {
        if (!has(CapitalsCapability.REGISTRY)) {
            return List.of();
        }
        Object all = call(CapitalsBinding.ALL_CAPITALS);
        if (!(all instanceof Collection<?> records)) {
            return List.of();
        }
        List<CapitalRef> capitals = new ArrayList<>();
        for (Object record : records) {
            wrap(record).ifPresent(capitals::add);
        }
        return List.copyOf(capitals);
    }

    @Override
    public Optional<ServerLevel> capitalLevel(MinecraftServer server, CapitalRef capital) {
        if (server == null || capital == null || !has(CapitalsCapability.REGISTRY)) {
            return Optional.empty();
        }
        Object level = call(CapitalsBinding.CAPITAL_LEVEL, server, capital.record());
        return level instanceof ServerLevel serverLevel ? Optional.of(serverLevel) : Optional.empty();
    }

    @Override
    public Optional<CapitalRef> capitalOfResident(UUID residentId) {
        if (residentId == null || !has(CapitalsCapability.REGISTRY)) {
            return Optional.empty();
        }
        return wrap(call(CapitalsBinding.CAPITAL_OF_RESIDENT, residentId));
    }

    @Override
    public boolean isActive(CapitalRef capital) {
        if (capital == null || !has(CapitalsCapability.REGISTRY)) {
            return false;
        }
        return STATE_ACTIVE.equals(enumName(call(CapitalsBinding.REC_STATE, capital.record())));
    }

    @Override
    public Optional<String> displayName(ServerLevel level, CapitalRef capital, UUID entityId) {
        if (level == null || capital == null || !has(CapitalsCapability.REGISTRY)) {
            return Optional.empty();
        }
        Object name = call(CapitalsBinding.DISPLAY_NAME, level, capital.record(), entityId);
        return name instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    // --- who holds what --------------------------------------------------------------------------

    @Override
    public List<UUID> villagerRoleHolders(ServerLevel level, CapitalRef capital, CapitalRole role) {
        if (level == null || capital == null || role == null || !role.appliesToVillager()
                || !has(CapitalsCapability.ROLES)) {
            return List.of();
        }
        Object record = capital.record();
        return switch (role) {
            case SOVEREIGN -> single(CapitalsBinding.REC_SOVEREIGN, record);
            case CONSORT -> single(CapitalsBinding.REC_CONSORT, record);
            case DOWAGER -> single(CapitalsBinding.REC_DOWAGER, record);
            case HEIR -> single(CapitalsBinding.REC_HEIR, record);
            case HAND -> single(CapitalsBinding.REC_HAND, record);
            case COMMANDER -> single(CapitalsBinding.REC_COMMANDER, record);
            case HERALD -> single(CapitalsBinding.REC_HERALD, record);
            case GRAND_MAESTER -> single(CapitalsBinding.REC_GRAND_MAESTER, record);
            case MASTER_OF_LAWS -> single(CapitalsBinding.REC_MASTER_OF_LAWS, record);
            case AMBASSADOR -> uuidOrEmpty(call(CapitalsBinding.AMBASSADOR, level, record));
            case ROYAL_CHILD -> set(CapitalsBinding.REC_ROYAL_CHILDREN, record);
            case DUKE -> set(CapitalsBinding.REC_DUKES, record);
            case LORD -> set(CapitalsBinding.REC_LORDS, record);
            case KNIGHT -> set(CapitalsBinding.REC_KNIGHTS, record);
            case ROYAL_GUARD -> set(CapitalsBinding.REC_ROYAL_GUARDS, record);
            case MEMBER -> members(level, record);
            // A player-only title. Never a villager, so never a holder here.
            case ARCHDUKE -> List.of();
        };
    }

    /**
     * Everyone the capital counts as one of its own.
     *
     * <p>Mirrors Capitals' own private {@code CapitalManager.belongsToCapital}, minus the two player
     * fields it also checks — a UUID returned from here is handed to an entity lookup, and a player id
     * would resolve to nothing and read as a missing villager rather than as a player.
     */
    private List<UUID> members(ServerLevel level, Object record) {
        Set<UUID> union = new LinkedHashSet<>();
        for (CapitalsBinding.Member office : List.of(CapitalsBinding.REC_SOVEREIGN,
                CapitalsBinding.REC_CONSORT, CapitalsBinding.REC_DOWAGER, CapitalsBinding.REC_HEIR,
                CapitalsBinding.REC_HAND, CapitalsBinding.REC_COMMANDER, CapitalsBinding.REC_HERALD,
                CapitalsBinding.REC_GRAND_MAESTER, CapitalsBinding.REC_MASTER_OF_LAWS)) {
            union.addAll(single(office, record));
        }
        union.addAll(uuidOrEmpty(call(CapitalsBinding.AMBASSADOR, level, record)));
        for (CapitalsBinding.Member rank : List.of(CapitalsBinding.REC_ROYAL_CHILDREN,
                CapitalsBinding.REC_DUKES, CapitalsBinding.REC_LORDS, CapitalsBinding.REC_KNIGHTS,
                CapitalsBinding.REC_ROYAL_GUARDS)) {
            union.addAll(set(rank, record));
        }
        return List.copyOf(union);
    }

    @Override
    public boolean villagerHasRole(ServerLevel level, CapitalRef capital, UUID villager, CapitalRole role) {
        if (level == null || capital == null || villager == null || role == null
                || !role.appliesToVillager() || !has(CapitalsCapability.ROLES)) {
            return false;
        }
        Object record = capital.record();
        return switch (role) {
            case ROYAL_CHILD -> callBoolean(CapitalsBinding.REC_IS_ROYAL_CHILD, record, villager);
            case DUKE -> callBoolean(CapitalsBinding.REC_IS_DUKE, record, villager);
            case LORD -> callBoolean(CapitalsBinding.REC_IS_LORD, record, villager);
            case KNIGHT -> callBoolean(CapitalsBinding.REC_IS_KNIGHT, record, villager);
            case ROYAL_GUARD -> callBoolean(CapitalsBinding.REC_IS_ROYAL_GUARD, record, villager);
            case MEMBER -> belongsToCapital(level, record, villager);
            case ARCHDUKE -> false;
            default -> villagerRoleHolders(level, capital, role).contains(villager);
        };
    }

    /**
     * The predicate form of {@link #members}, and the closer mirror of Capitals' own: it can also ask
     * about the household and the disgraced, which have a membership test but no readable set.
     */
    private boolean belongsToCapital(ServerLevel level, Object record, UUID villager) {
        for (CapitalsBinding.Member office : List.of(CapitalsBinding.REC_SOVEREIGN,
                CapitalsBinding.REC_CONSORT, CapitalsBinding.REC_DOWAGER, CapitalsBinding.REC_HEIR,
                CapitalsBinding.REC_HAND, CapitalsBinding.REC_COMMANDER, CapitalsBinding.REC_HERALD,
                CapitalsBinding.REC_GRAND_MAESTER, CapitalsBinding.REC_MASTER_OF_LAWS)) {
            if (villager.equals(uuidOrNull(call(office, record)))) {
                return true;
            }
        }
        if (villager.equals(uuidOrNull(call(CapitalsBinding.AMBASSADOR, level, record)))) {
            return true;
        }
        for (CapitalsBinding.Member test : List.of(CapitalsBinding.REC_IS_ROYAL_CHILD,
                CapitalsBinding.REC_IS_DUKE, CapitalsBinding.REC_IS_LORD, CapitalsBinding.REC_IS_KNIGHT,
                CapitalsBinding.REC_IS_ROYAL_GUARD, CapitalsBinding.REC_IS_HOUSEHOLD,
                CapitalsBinding.REC_IS_DISINHERITED, CapitalsBinding.REC_IS_LEGITIMIZED,
                CapitalsBinding.REC_IS_DISGRACED_GUARD)) {
            if (callBoolean(test, record, villager)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean playerHasRole(ServerLevel level, CapitalRef capital, UUID player, CapitalRole role) {
        if (level == null || capital == null || player == null || role == null
                || !role.appliesToPlayer() || !has(CapitalsCapability.PLAYER_TITLES)) {
            return false;
        }
        Object record = capital.record();
        return switch (role) {
            case SOVEREIGN -> callBoolean(CapitalsBinding.REC_IS_PLAYER_SOVEREIGN, record)
                    && player.equals(uuidOrNull(call(CapitalsBinding.REC_PLAYER_SOVEREIGN_ID, record)));
            case CONSORT -> callBoolean(CapitalsBinding.REC_IS_PLAYER_CONSORT, record)
                    && player.equals(uuidOrNull(call(CapitalsBinding.REC_PLAYER_CONSORT_ID, record)));
            case HAND -> callBoolean(CapitalsBinding.PLAYER_IS_HAND, level, record, player);
            case COMMANDER -> callBoolean(CapitalsBinding.PLAYER_IS_COMMANDER, level, record, player);
            case KNIGHT -> holdsTitle(level, record, player, "KNIGHT", "DAME");
            case LORD -> holdsTitle(level, record, player, "LORD", "LADY");
            case DUKE -> holdsTitle(level, record, player, "DUKE", "DUCHESS");
            case ARCHDUKE -> holdsTitle(level, record, player, "ARCHDUKE", "ARCHDUCHESS");
            case MEMBER -> playerHasRole(level, capital, player, CapitalRole.SOVEREIGN)
                    || playerHasRole(level, capital, player, CapitalRole.CONSORT)
                    || playerHasRole(level, capital, player, CapitalRole.HAND)
                    || playerHasRole(level, capital, player, CapitalRole.COMMANDER)
                    || grantedTitle(level, record, player).isPresent();
            default -> false;
        };
    }

    /** True when the player's granted title is either half of a gendered pair. */
    private boolean holdsTitle(ServerLevel level, Object record, UUID player, String masculine,
                               String feminine) {
        Optional<String> title = grantedTitle(level, record, player);
        return title.filter(name -> name.equals(masculine) || name.equals(feminine)).isPresent();
    }

    /** The player's granted {@code NobleTitle} by name, or empty when they hold none. */
    private Optional<String> grantedTitle(ServerLevel level, Object record, UUID player) {
        String name = enumName(call(CapitalsBinding.GRANTED_TITLE, level, record, player));
        // COMMONER is Capitals' "no title", not a rank; treating it as one would make every villager's
        // neighbour a noble.
        return name == null || name.equals("COMMONER") ? Optional.empty() : Optional.of(name);
    }

    @Override
    public Optional<UUID> declaredAllegiance(ServerLevel level, UUID player) {
        if (level == null || player == null || !has(CapitalsCapability.ALLEGIANCE)) {
            return Optional.empty();
        }
        return Optional.ofNullable(uuidOrNull(call(CapitalsBinding.DECLARED_CAPITAL, level, player)));
    }

    // --- diplomacy and succession ------------------------------------------------------------------

    @Override
    public Optional<String> diplomaticState(ServerLevel level, UUID first, UUID second) {
        if (level == null || first == null || second == null || !has(CapitalsCapability.DIPLOMACY)) {
            return Optional.empty();
        }
        return Optional.ofNullable(enumName(call(CapitalsBinding.DIPLOMATIC_STATE, level, first, second)));
    }

    @Override
    public Map<UUID, InterregnumView> interregnums(ServerLevel level) {
        if (level == null || !has(CapitalsCapability.INTERREGNUM)) {
            return Map.of();
        }
        Object snapshot = call(CapitalsBinding.INTERREGNUM_SNAPSHOT, level);
        if (!(snapshot instanceof Map<?, ?> records)) {
            return Map.of();
        }
        Map<UUID, InterregnumView> views = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : records.entrySet()) {
            if (entry.getKey() instanceof UUID capitalId && entry.getValue() != null) {
                views.put(capitalId, view(capitalId, entry.getValue()));
            }
        }
        return Map.copyOf(views);
    }

    @Override
    public Optional<InterregnumView> interregnum(ServerLevel level, CapitalRef capital) {
        if (level == null || capital == null || !has(CapitalsCapability.INTERREGNUM)) {
            return Optional.empty();
        }
        Object record = call(CapitalsBinding.INTERREGNUM_RECORD, level, capital.capitalId());
        return record == null ? Optional.empty() : Optional.of(view(capital.capitalId(), record));
    }

    private InterregnumView view(UUID capitalId, Object record) {
        return new InterregnumView(capitalId, uuidOrNull(call(CapitalsBinding.IR_DECEASED, record)),
                callBoolean(CapitalsBinding.IR_WAS_PLAYER, record));
    }

    // --- the three mutations ------------------------------------------------------------------------

    @Override
    public boolean grantPlayerTitle(ServerLevel level, CapitalRef capital, UUID player,
                                    String nobleTitleName) {
        if (level == null || capital == null || player == null || nobleTitleName == null
                || !has(CapitalsCapability.TITLE_GRANTS)) {
            return false;
        }
        Object title = enumConstant(CapitalsBinding.CLASS_NOBLE_TITLE, nobleTitleName);
        if (title == null) {
            return false;
        }
        // grantTitle marks its own saved data dirty (and does not announce), so nothing follows it.
        return callVoid(CapitalsBinding.GRANT_TITLE, level, capital.record(), player, title);
    }

    @Override
    public boolean setVillagerTitle(ServerLevel level, CapitalRef capital, UUID villager,
                                    String nobleTitleName) {
        if (level == null || capital == null || villager == null || nobleTitleName == null
                || !has(CapitalsCapability.VILLAGER_TITLES)) {
            return false;
        }
        String title = nobleTitleName.toUpperCase(Locale.ROOT);
        // The gender rides in the constant itself, exactly as it does for a player grant, so the
        // caller decides it once and both paths agree.
        boolean female = title.equals("DAME") || title.equals("LADY") || title.equals("DUCHESS");
        CapitalsBinding.Member member = switch (title) {
            case "KNIGHT", "DAME" -> CapitalsBinding.REC_ADD_KNIGHT;
            case "LORD", "LADY" -> CapitalsBinding.REC_ADD_LORD;
            case "DUKE", "DUCHESS" -> CapitalsBinding.REC_ADD_DUKE;
            default -> null;
        };
        if (member == null || !callVoid(member, capital.record(), villager, female)) {
            return false;
        }
        markDirty(level);
        return true;
    }

    @Override
    public boolean addChronicleEntry(ServerLevel level, CapitalRef capital, String entry, boolean herald) {
        if (level == null || capital == null || entry == null || entry.isBlank()
                || !has(CapitalsCapability.CHRONICLE)) {
            return false;
        }
        boolean written = herald
                ? callVoid(CapitalsBinding.CHRONICLE_ENTRY, level, capital.record(), entry)
                : callVoid(CapitalsBinding.CHRONICLE_ENTRY_QUIET, capital.record(), entry);
        if (!written) {
            return false;
        }
        markDirty(level);
        return true;
    }

    /**
     * Mirrors Capitals' in-memory registry back into its saved data. Required after any write to a
     * capital record: {@code CapitalManager} is a plain map, so a title or chronicle line that is not
     * followed by this is lost at the next restart.
     */
    private void markDirty(ServerLevel level) {
        callVoid(CapitalsBinding.MARK_DIRTY, level);
    }

    // --- gender ------------------------------------------------------------------------------------

    @Override
    public Optional<Boolean> isPlayerFemale(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null || !resolution.has(CapitalsBinding.PLAYER_IS_FEMALE)) {
            return Optional.empty();
        }
        return Optional.of(callBoolean(CapitalsBinding.PLAYER_IS_FEMALE, level, player));
    }

    @Override
    public Optional<Boolean> isVillagerFemale(ServerLevel level, UUID villager) {
        if (level == null || villager == null || !resolution.has(CapitalsBinding.VILLAGER_IS_FEMALE)) {
            return Optional.empty();
        }
        return Optional.of(callBoolean(CapitalsBinding.VILLAGER_IS_FEMALE, level, villager));
    }

    // --- plumbing ------------------------------------------------------------------------------------

    /** A capital record wrapped as the opaque handle callers hold, or empty when it is unusable. */
    private Optional<CapitalRef> wrap(@Nullable Object record) {
        if (record == null) {
            return Optional.empty();
        }
        UUID capitalId = uuidOrNull(call(CapitalsBinding.REC_CAPITAL_ID, record));
        Object villageId = call(CapitalsBinding.REC_VILLAGE_ID, record);
        if (capitalId == null || !(villageId instanceof Integer village)) {
            return Optional.empty();
        }
        Object dimension = call(CapitalsBinding.REC_DIMENSION, record);
        return Optional.of(new CapitalRef(record, capitalId, village,
                dimension instanceof String text ? text : ""));
    }

    private List<UUID> single(CapitalsBinding.Member member, Object record) {
        return uuidOrEmpty(call(member, record));
    }

    private List<UUID> set(CapitalsBinding.Member member, Object record) {
        Object holders = call(member, record);
        if (!(holders instanceof Collection<?> values)) {
            return List.of();
        }
        List<UUID> uuids = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof UUID uuid) {
                uuids.add(uuid);
            }
        }
        return List.copyOf(uuids);
    }

    private static List<UUID> uuidOrEmpty(@Nullable Object value) {
        UUID uuid = uuidOrNull(value);
        return uuid == null ? List.of() : List.of(uuid);
    }

    @Nullable
    private static UUID uuidOrNull(@Nullable Object value) {
        return value instanceof UUID uuid ? uuid : null;
    }

    /** The {@link Enum#name()} of a Capitals enum value, without naming its type. */
    @Nullable
    private static String enumName(@Nullable Object value) {
        return value instanceof Enum<?> constant ? constant.name() : null;
    }

    /** One constant of a Capitals enum, by name. Null when the class or the constant is not there. */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object enumConstant(CapitalsBinding.Member classMember, String name) {
        Class<?> type = resolution.type(classMember);
        if (type == null || !type.isEnum()) {
            return null;
        }
        try {
            return Enum.valueOf((Class<? extends Enum>) type, name.toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            report(classMember, t);
            return null;
        }
    }

    @Nullable
    private Object call(CapitalsBinding.Member member, Object... args) {
        try {
            return resolution.handle(member).invokeWithArguments(args);
        } catch (Throwable t) {
            report(member, t);
            return null;
        }
    }

    private boolean callBoolean(CapitalsBinding.Member member, Object... args) {
        return call(member, args) instanceof Boolean value && value;
    }

    /** Invokes a {@code void} member; the boolean is "it ran", not a return value. */
    private boolean callVoid(CapitalsBinding.Member member, Object... args) {
        if (!resolution.has(member)) {
            return false;
        }
        try {
            resolution.handle(member).invokeWithArguments(args);
            return true;
        } catch (Throwable t) {
            report(member, t);
            return false;
        }
    }

    /**
     * One debug line per member that misbehaves, ever.
     *
     * <p>Debug rather than warn, and once rather than per call: the two callers most likely to hit a
     * broken handle are an offer filter and a tick poller, and a warning from either would fill a log
     * faster than it explained anything.
     */
    private void report(CapitalsBinding.Member member, Throwable t) {
        if (reported.add(member.toString())) {
            McaQuests.LOGGER.debug("[MCA: Quests] MCA Capitals member {} failed; that part of the "
                    + "integration answers as unavailable from here on.", member, t);
        }
    }
}
