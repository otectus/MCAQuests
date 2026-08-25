package dev.otectus.mcaquests.compat.mca;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The typed facade over {@link McaBinding}: MCA's API expressed entirely in vanilla and JDK types.
 *
 * <p>Every field here is {@code static final} and assigned once in {@code <clinit>}, which is what
 * keeps this fast — HotSpot constant-folds a {@code static final} {@link Class} or
 * {@link MethodHandle}, so {@link #isVillager} folds to the same check {@code instanceof} would emit
 * and a bound handle inlines through to MCA's method. A map lookup per call would forfeit both. The
 * map lookups therefore happen exactly once, here, at class initialisation.
 *
 * <p><b>Nothing in this class can throw.</b> Unbound members are constant stubs (see
 * {@link McaBinding.Resolution#handle}), and every accessor below additionally swallows
 * {@link Throwable} and returns its documented empty value. That is deliberate belt-and-braces: these
 * are called from Forge event handlers where an escaping error kills the server, which is precisely
 * the bug this layer was built to fix.
 *
 * <p>MCA enums are never handed out as MCA types — reads return the lowercase {@code name()} and the
 * two places that must pass one back in ({@code MoveState}) use a cached constant. So no MCA type
 * appears in any signature, and {@code NoMcaStaticLinkTest} can enforce that no compiled class
 * references one.
 */
public final class McaHandles {

    private static final McaBinding.Resolution R = resolveQuietly();

    private static McaBinding.Resolution resolveQuietly() {
        try {
            return McaBinding.resolveAgainst(McaHandles.class.getClassLoader());
        } catch (Throwable t) {
            return McaBinding.absent();
        }
    }

    /** The live resolution, for logging and {@code /mcaquests debug mca}. */
    public static McaBinding.Resolution resolution() {
        return R;
    }

    /** True when MCA bound well enough to be useful. */
    public static boolean available() {
        return VILLAGER != null;
    }

    // --- classes ---------------------------------------------------------------------------------
    private static final Class<?> VILLAGER = R.cls(McaBinding.VILLAGER_CLASS);
    private static final Class<?> VILLAGER_LIKE = R.cls(McaBinding.VILLAGER_LIKE_CLASS);

    // --- handles ---------------------------------------------------------------------------------
    private static final MethodHandle H_BRAIN = R.handle(McaBinding.GET_VILLAGER_BRAIN);
    private static final MethodHandle H_RESIDENCY = R.handle(McaBinding.GET_RESIDENCY);
    private static final MethodHandle H_INFECTION = R.handle(McaBinding.GET_INFECTION_PROGRESS);
    private static final MethodHandle H_SET_PROFESSION = R.handle(McaBinding.SET_PROFESSION);

    private static final MethodHandle H_PROFESSION_ID = R.handle(McaBinding.GET_PROFESSION_ID);
    private static final MethodHandle H_PROFESSION_TEXT = R.handle(McaBinding.GET_PROFESSION_TEXT);
    private static final MethodHandle H_AGE_STATE = R.handle(McaBinding.GET_AGE_STATE);
    private static final MethodHandle H_INITIALIZE = R.handle(McaBinding.INITIALIZE);
    private static final MethodHandle H_SET_NAME = R.handle(McaBinding.SET_NAME);

    private static final MethodHandle H_MEMORIES_FOR = R.handle(McaBinding.GET_MEMORIES_FOR_PLAYER);
    private static final MethodHandle H_REWARD_HEARTS = R.handle(McaBinding.REWARD_HEARTS);
    private static final MethodHandle H_SET_MOVE_STATE = R.handle(McaBinding.SET_MOVE_STATE);
    private static final MethodHandle H_GET_MOVE_STATE = R.handle(McaBinding.GET_MOVE_STATE);
    private static final MethodHandle H_PERSONALITY = R.handle(McaBinding.GET_PERSONALITY);
    private static final MethodHandle H_MOOD_VALUE = R.handle(McaBinding.GET_MOOD_VALUE);
    private static final MethodHandle H_MOOD = R.handle(McaBinding.GET_MOOD);
    private static final MethodHandle H_HEARTS = R.handle(McaBinding.GET_HEARTS);
    private static final MethodHandle H_MOOD_NAME = R.handle(McaBinding.MOOD_GET_NAME);

    private static final MethodHandle H_HOME_VILLAGE = R.handle(McaBinding.GET_HOME_VILLAGE);
    private static final MethodHandle H_HOME = R.handle(McaBinding.GET_HOME);
    private static final MethodHandle H_WORKPLACE = R.handle(McaBinding.GET_WORKPLACE);

    private static final MethodHandle H_RELATIONSHIP_OF = R.handle(McaBinding.RELATIONSHIP_OF);
    private static final MethodHandle H_IS_MARRIED_TO = R.handle(McaBinding.IS_MARRIED_TO);
    private static final MethodHandle H_IS_MARRIED = R.handle(McaBinding.IS_MARRIED);
    private static final MethodHandle H_RELATIONSHIP_STATE = R.handle(McaBinding.GET_RELATIONSHIP_STATE);
    private static final MethodHandle H_FAMILY_ENTRY = R.handle(McaBinding.GET_FAMILY_ENTRY);
    private static final MethodHandle H_FAMILY_TREE = R.handle(McaBinding.GET_FAMILY_TREE);

    private static final MethodHandle H_GENDER_RANDOM = R.handle(McaBinding.GENDER_RANDOM);
    private static final MethodHandle H_GENDER_BINARY = R.handle(McaBinding.GENDER_BINARY);
    private static final MethodHandle H_GENDER_TYPE = R.handle(McaBinding.GENDER_VILLAGER_TYPE);

    private static final MethodHandle H_TREE_GET = R.handle(McaBinding.FAMILY_TREE_GET);
    private static final MethodHandle H_TREE_GET_OR_EMPTY = R.handle(McaBinding.FAMILY_TREE_GET_OR_EMPTY);
    private static final MethodHandle H_TREE_REMOVE = R.handle(McaBinding.FAMILY_TREE_REMOVE);

    private static final MethodHandle H_NODE_ID = R.handle(McaBinding.NODE_ID);
    private static final MethodHandle H_NODE_DECEASED = R.handle(McaBinding.NODE_IS_DECEASED);
    private static final MethodHandle H_NODE_PLAYER = R.handle(McaBinding.NODE_IS_PLAYER);
    private static final MethodHandle H_NODE_GENERATED = R.handle(McaBinding.NODE_PROBABLY_GENERATED);
    private static final MethodHandle H_NODE_GENDER = R.handle(McaBinding.NODE_GENDER);
    private static final MethodHandle H_NODE_NAME = R.handle(McaBinding.NODE_GET_NAME);
    private static final MethodHandle H_NODE_PROFESSION = R.handle(McaBinding.NODE_GET_PROFESSION);
    private static final MethodHandle H_NODE_PARTNER = R.handle(McaBinding.NODE_PARTNER);
    private static final MethodHandle H_NODE_PARENTS = R.handle(McaBinding.NODE_STREAM_PARENTS);
    private static final MethodHandle H_NODE_CHILDREN = R.handle(McaBinding.NODE_STREAM_CHILDREN);
    private static final MethodHandle H_NODE_SIBLINGS = R.handle(McaBinding.NODE_SIBLINGS);
    private static final MethodHandle H_NODE_IS_RELATIVE = R.handle(McaBinding.NODE_IS_RELATIVE);
    private static final MethodHandle H_NODE_IS_PARENT = R.handle(McaBinding.NODE_IS_PARENT);
    private static final MethodHandle H_NODE_PARENT_NODES = R.handle(McaBinding.NODE_GET_PARENTS);

    private static final MethodHandle H_PLAYER_SAVE = R.handle(McaBinding.PLAYER_SAVE_GET);

    private static final MethodHandle H_VILLAGE_ID = R.handle(McaBinding.VILLAGE_GET_ID);
    private static final MethodHandle H_VILLAGE_NAME = R.handle(McaBinding.VILLAGE_GET_NAME);
    private static final MethodHandle H_VILLAGE_CENTER = R.handle(McaBinding.VILLAGE_GET_CENTER);
    private static final MethodHandle H_VILLAGE_BORDER = R.handle(McaBinding.VILLAGE_IS_WITHIN_BORDER);
    private static final MethodHandle H_VILLAGE_UUIDS = R.handle(McaBinding.VILLAGE_RESIDENT_UUIDS);
    private static final MethodHandle H_VILLAGE_RESIDENTS = R.handle(McaBinding.VILLAGE_GET_RESIDENTS);
    private static final MethodHandle H_VILLAGE_HAS_RESIDENT = R.handle(McaBinding.VILLAGE_HAS_RESIDENT);
    private static final MethodHandle H_VILLAGE_STORAGE = R.handle(McaBinding.VILLAGE_STORAGE_BUFFER);
    private static final MethodHandle H_VILLAGE_BUILDINGS = R.handle(McaBinding.VILLAGE_GET_BUILDINGS);
    private static final MethodHandle H_VILLAGE_BUILDINGS_OF_TYPE =
            R.handle(McaBinding.VILLAGE_BUILDINGS_OF_TYPE);
    private static final MethodHandle H_BUILDING_ID = R.handle(McaBinding.BUILDING_GET_ID);
    private static final MethodHandle H_BUILDING_TYPE = R.handle(McaBinding.BUILDING_GET_TYPE);
    private static final MethodHandle H_BUILDING_SIZE = R.handle(McaBinding.BUILDING_GET_SIZE);
    private static final MethodHandle H_BUILDING_CENTER = R.handle(McaBinding.BUILDING_GET_CENTER);
    private static final boolean HAS_HAS_RESIDENT = R.has(McaBinding.VILLAGE_HAS_RESIDENT);

    private static final MethodHandle H_MANAGER_GET = R.handle(McaBinding.VILLAGE_MANAGER_GET);
    private static final MethodHandle H_MANAGER_BY_ID = R.handle(McaBinding.VILLAGE_MANAGER_GET_OR_EMPTY);
    private static final MethodHandle H_NEAREST_VILLAGE = R.handle(McaBinding.FIND_NEAREST_VILLAGE);

    /** MCA {@code MoveState} constants, resolved once. Null when MCA is absent — callers no-op. */
    private static final Object MOVE_STATE_FOLLOW = R.enumConstant(McaBinding.MOVE_STATE_CLASS, "FOLLOW");
    private static final Object MOVE_STATE_MOVE = R.enumConstant(McaBinding.MOVE_STATE_CLASS, "MOVE");

    private McaHandles() {
    }

    // ---------------------------------------------------------------------------------------------
    // Type tests — the hot path. No MethodHandle involved: a constant-folded Class.isInstance is the
    // same check `instanceof` compiles to, and isVillager runs on every entity right-click.
    // ---------------------------------------------------------------------------------------------

    /** True for an MCA human villager entity (adult or child; not the zombie variant). */
    public static boolean isVillager(Object entity) {
        Class<?> type = VILLAGER;
        return type != null && type.isInstance(entity);
    }

    /**
     * True for anything implementing MCA's {@code VillagerLike}. Deliberately distinct from
     * {@link #isVillager}: player-backed pseudo-villagers are {@code VillagerLike} but are not
     * {@code VillagerEntityMCA}, and profession/age reads must still work for them.
     */
    public static boolean isVillagerLike(Object entity) {
        Class<?> type = VILLAGER_LIKE;
        return type != null && type.isInstance(entity);
    }

    /**
     * Every loaded MCA villager whose bounding box intersects {@code box}. Keeps the bound
     * {@code VillagerEntityMCA} class private to this class: the unchecked cast is safe because
     * generics are erased at runtime and the returned list genuinely holds MCA villagers, so callers
     * get a plain {@code List<Entity>} and no MCA type reaches their signatures. Bounded but not
     * cheap — callers must throttle.
     */
    @SuppressWarnings("unchecked")
    public static List<Entity> villagersWithin(Level level, AABB box) {
        Class<?> type = VILLAGER;
        if (type == null || level == null) {
            return List.of();
        }
        try {
            return level.getEntitiesOfClass((Class<Entity>) type, box);
        } catch (Throwable t) {
            return List.of();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Invocation helpers. Each swallows Throwable and yields the documented empty value.
    // ---------------------------------------------------------------------------------------------

    private static Object ref(MethodHandle handle, Object receiver) {
        if (receiver == null) {
            return null;
        }
        try {
            return handle.invoke(receiver);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object ref(MethodHandle handle, Object receiver, Object a) {
        if (receiver == null) {
            return null;
        }
        try {
            return handle.invoke(receiver, a);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean bool(MethodHandle handle, Object receiver, Object a) {
        if (receiver == null) {
            return false;
        }
        try {
            return (boolean) handle.invoke(receiver, a);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Unwraps a {@code java.util.Optional} MCA returned, to the payload or null. */
    private static Object unwrap(Object maybeOptional) {
        return maybeOptional instanceof Optional<?> opt ? opt.orElse(null) : null;
    }

    /** Lowercased {@code name()} of an MCA enum value, or null — how every enum read leaves this class. */
    private static String enumName(Object value) {
        return value instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> uuids(Object stream) {
        if (stream instanceof Stream<?> s) {
            try {
                return new ArrayList<>(((Stream<UUID>) s).toList());
            } catch (Throwable t) {
                return List.of();
            }
        }
        return List.of();
    }

    // ---------------------------------------------------------------------------------------------
    // VillagerEntityMCA / VillagerLike
    // ---------------------------------------------------------------------------------------------

    /** MCA's {@code VillagerBrain} for this villager, or null. */
    public static Object brain(Object villager) {
        return isVillager(villager) ? ref(H_BRAIN, villager) : null;
    }

    /** MCA's {@code Residency} for this villager, or null. */
    public static Object residency(Object villager) {
        return isVillager(villager) ? ref(H_RESIDENCY, villager) : null;
    }

    public static float infectionProgress(Object villager) {
        if (!isVillager(villager)) {
            return 0f;
        }
        try {
            return (float) H_INFECTION.invoke(villager);
        } catch (Throwable t) {
            return 0f;
        }
    }

    public static void setProfession(Object villager, Object profession) {
        if (isVillager(villager) && profession != null) {
            try {
                H_SET_PROFESSION.invoke(villager, profession);
            } catch (Throwable ignored) {
                // MCA drift must never break materialisation; the villager simply keeps its rolled job.
            }
        }
    }

    public static ResourceLocation professionId(Object villagerLike) {
        return isVillagerLike(villagerLike) && ref(H_PROFESSION_ID, villagerLike) instanceof ResourceLocation id
                ? id : null;
    }

    public static Component professionText(Object villagerLike) {
        return isVillagerLike(villagerLike) && ref(H_PROFESSION_TEXT, villagerLike) instanceof Component text
                ? text : null;
    }

    /** Lowercased MCA age-state name (e.g. {@code "adult"}), or null. */
    public static String ageStateName(Object villagerLike) {
        return isVillagerLike(villagerLike) ? enumName(ref(H_AGE_STATE, villagerLike)) : null;
    }

    public static void initialize(Object villagerLike, MobSpawnType spawnType) {
        if (isVillagerLike(villagerLike)) {
            try {
                H_INITIALIZE.invoke(villagerLike, spawnType);
            } catch (Throwable ignored) {
                // Caller checks the result of the wider materialisation; a failed init is not fatal here.
            }
        }
    }

    public static void setName(Object villagerLike, String name) {
        if (isVillagerLike(villagerLike) && name != null) {
            try {
                H_SET_NAME.invoke(villagerLike, name);
            } catch (Throwable ignored) {
                // The villager keeps MCA's rolled name; still the right person by UUID.
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // VillagerBrain
    // ---------------------------------------------------------------------------------------------

    /** Player-relationship hearts, MCA's "favor". 0 when unavailable. */
    public static int hearts(Object villager, Player player) {
        Object memories = ref(H_MEMORIES_FOR, brain(villager), player);
        if (memories == null) {
            return 0;
        }
        try {
            return (int) H_HEARTS.invoke(memories);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void rewardHearts(Object villager, ServerPlayer player, int amount) {
        Object brain = brain(villager);
        if (brain == null) {
            return;
        }
        try {
            H_REWARD_HEARTS.invoke(brain, player, amount);
        } catch (Throwable ignored) {
            // Hearts are cosmetic-ish progression; never let a reward payout kill the tick.
        }
    }

    /** Lowercased MCA move-state name, or null. */
    public static String moveStateName(Object villager) {
        return enumName(ref(H_GET_MOVE_STATE, brain(villager)));
    }

    private static void setMoveState(Object villager, Object state, Player player) {
        Object brain = brain(villager);
        if (brain == null || state == null) {
            return;
        }
        try {
            H_SET_MOVE_STATE.invoke(brain, state, player);
        } catch (Throwable ignored) {
            // Movement control is best-effort: the villager just keeps its current state.
        }
    }

    public static void setFollow(Object villager, Player player) {
        setMoveState(villager, MOVE_STATE_FOLLOW, player);
    }

    public static void setMove(Object villager, Player player) {
        setMoveState(villager, MOVE_STATE_MOVE, player);
    }

    public static boolean isFollowing(Object villager) {
        return "follow".equals(moveStateName(villager));
    }

    public static boolean isMoving(Object villager) {
        return "move".equals(moveStateName(villager));
    }

    public static String personalityName(Object villager) {
        return enumName(ref(H_PERSONALITY, brain(villager)));
    }

    /** MCA mood value, or {@link Integer#MIN_VALUE} when unavailable (callers map that to empty). */
    public static int moodValue(Object villager) {
        Object brain = brain(villager);
        if (brain == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return (int) H_MOOD_VALUE.invoke(brain);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    /** MCA mood name, already lowercased. Unlike the enums this is MCA's own {@code String}. */
    public static String moodName(Object villager) {
        Object name = ref(H_MOOD_NAME, ref(H_MOOD, brain(villager)));
        return name instanceof String s && !s.isBlank() ? s.toLowerCase(Locale.ROOT) : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Residency
    // ---------------------------------------------------------------------------------------------

    /** MCA {@code Village} this villager calls home, or null. */
    public static Object homeVillage(Object villager) {
        return unwrap(ref(H_HOME_VILLAGE, residency(villager)));
    }

    public static GlobalPos homePos(Object villager) {
        return unwrap(ref(H_HOME, residency(villager))) instanceof GlobalPos pos ? pos : null;
    }

    public static BlockPos workplace(Object villager) {
        return ref(H_WORKPLACE, residency(villager)) instanceof BlockPos pos ? pos : null;
    }

    // ---------------------------------------------------------------------------------------------
    // EntityRelationship / family tree
    // ---------------------------------------------------------------------------------------------

    /** MCA {@code EntityRelationship} for an entity, or null. */
    public static Object relationshipOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return unwrap(H_RELATIONSHIP_OF.invoke(entity));
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isMarriedTo(Object relationship, UUID uuid) {
        return bool(H_IS_MARRIED_TO, relationship, uuid);
    }

    public static boolean isMarried(Object relationship) {
        if (relationship == null) {
            return false;
        }
        try {
            return (boolean) H_IS_MARRIED.invoke(relationship);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String relationshipStateName(Object relationship) {
        return enumName(ref(H_RELATIONSHIP_STATE, relationship));
    }

    /** The relationship's own {@code FamilyTreeNode}, or null. */
    public static Object familyEntry(Object relationship) {
        return ref(H_FAMILY_ENTRY, relationship);
    }

    /** The {@code FamilyTree} reachable from a relationship, or null. */
    public static Object familyTree(Object relationship) {
        return ref(H_FAMILY_TREE, relationship);
    }

    /** The level's persistent {@code FamilyTree}, or null. */
    public static Object familyTree(ServerLevel level) {
        try {
            return H_TREE_GET.invoke(level);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The node for {@code uuid} in {@code tree}, or null. */
    public static Object node(Object tree, UUID uuid) {
        return unwrap(ref(H_TREE_GET_OR_EMPTY, tree, uuid));
    }

    public static void removeNode(Object tree, UUID uuid) {
        if (tree != null) {
            try {
                H_TREE_REMOVE.invoke(tree, uuid);
            } catch (Throwable ignored) {
                // Leaves a stray scratch node at worst; never worth failing a spawn over.
            }
        }
    }

    public static UUID nodeId(Object node) {
        return ref(H_NODE_ID, node) instanceof UUID id ? id : null;
    }

    public static boolean nodeDeceased(Object node) {
        return nodeFlag(H_NODE_DECEASED, node);
    }

    public static boolean nodeIsPlayer(Object node) {
        return nodeFlag(H_NODE_PLAYER, node);
    }

    public static boolean nodeProbablyGenerated(Object node) {
        return nodeFlag(H_NODE_GENERATED, node);
    }

    private static boolean nodeFlag(MethodHandle handle, Object node) {
        if (node == null) {
            return false;
        }
        try {
            return (boolean) handle.invoke(node);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The node's MCA {@code Gender}, already reduced to its binary form, or null. */
    public static Object nodeBinaryGender(Object node) {
        Object gender = ref(H_NODE_GENDER, node);
        return gender == null ? null : ref(H_GENDER_BINARY, gender);
    }

    public static Object randomBinaryGender() {
        try {
            Object gender = H_GENDER_RANDOM.invoke();
            return gender == null ? null : ref(H_GENDER_BINARY, gender);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The villager {@link EntityType} for an MCA {@code Gender}, or null. */
    @SuppressWarnings("unchecked")
    public static EntityType<? extends Entity> villagerTypeOf(Object gender) {
        return ref(H_GENDER_TYPE, gender) instanceof EntityType<?> type
                ? (EntityType<? extends Entity>) type : null;
    }

    public static String nodeName(Object node) {
        return ref(H_NODE_NAME, node) instanceof String s && !s.isBlank() ? s : null;
    }

    /** MCA's {@code VillagerProfession} for the node, kept opaque and handed straight back to MCA. */
    public static Object nodeProfession(Object node) {
        return ref(H_NODE_PROFESSION, node);
    }

    public static UUID nodePartner(Object node) {
        return ref(H_NODE_PARTNER, node) instanceof UUID id ? id : null;
    }

    public static List<UUID> nodeParents(Object node) {
        return uuids(ref(H_NODE_PARENTS, node));
    }

    public static List<UUID> nodeChildren(Object node) {
        return uuids(ref(H_NODE_CHILDREN, node));
    }

    @SuppressWarnings("unchecked")
    public static Set<UUID> nodeSiblings(Object node) {
        return ref(H_NODE_SIBLINGS, node) instanceof Set<?> set ? (Set<UUID>) set : Set.of();
    }

    public static boolean nodeIsRelative(Object node, UUID uuid) {
        return bool(H_NODE_IS_RELATIVE, node, uuid);
    }

    /** MCA direction convention: true when {@code uuid} is one of <em>this node's</em> parents. */
    public static boolean nodeIsParent(Object node, UUID uuid) {
        return bool(H_NODE_IS_PARENT, node, uuid);
    }

    /** The node's parent nodes (not UUIDs) — MCA returns a {@code Stream<FamilyTreeNode>}. */
    public static List<Object> nodeParentNodes(Object node) {
        return ref(H_NODE_PARENT_NODES, node) instanceof Stream<?> s
                ? new ArrayList<Object>(s.toList()) : List.of();
    }

    /** MCA's {@code PlayerSaveData} for a player, or null. It implements {@code EntityRelationship}. */
    public static Object playerSave(ServerPlayer player) {
        try {
            return H_PLAYER_SAVE.invoke(player);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Village / VillageManager
    // ---------------------------------------------------------------------------------------------

    /** The level's {@code VillageManager}, or null. */
    public static Object villageManager(ServerLevel level) {
        try {
            return H_MANAGER_GET.invoke(level);
        } catch (Throwable t) {
            return null;
        }
    }

    /** An MCA {@code Village} by id, or null. */
    public static Object village(ServerLevel level, int villageId) {
        return unwrap(ref(H_MANAGER_BY_ID, villageManager(level), villageId));
    }

    /**
     * Every building MCA has registered for a village, as opaque handles. MCA owns the building
     * registry and Townstead only contributes type ids to it, so "how many docks does this village
     * have" is an MCA question whose answer is cross-referenced against Townstead's ids -- not a
     * Townstead call. Empty when the village is unknown or the members did not bind.
     */
    public static List<Object> villageBuildings(Object village) {
        Object map = ref(H_VILLAGE_BUILDINGS, village);
        if (!(map instanceof Map<?, ?> buildings)) {
            return List.of();
        }
        List<Object> out = new ArrayList<>(buildings.size());
        for (Object building : buildings.values()) {
            if (building != null) {
                out.add(building);
            }
        }
        return out;
    }

    /** Buildings of one type. The type crosses as a plain String, so no MCA type is named. */
    public static List<Object> villageBuildingsOfType(Object village, String type) {
        Object stream = ref(H_VILLAGE_BUILDINGS_OF_TYPE, village, type);
        if (stream instanceof Stream<?> s) {
            try {
                return new ArrayList<>(s.toList());
            } catch (Throwable t) {
                return List.of();
            }
        }
        return List.of();
    }

    /** A building's registered type id, e.g. {@code dock_l2}. Empty string when unavailable. */
    public static String buildingType(Object building) {
        Object type = ref(H_BUILDING_TYPE, building);
        return type instanceof String s ? s : "";
    }

    public static int buildingSize(Object building) {
        return building == null ? 0 : intOf(H_BUILDING_SIZE, building);
    }

    public static int buildingId(Object building) {
        return building == null ? -1 : intOf(H_BUILDING_ID, building);
    }

    public static Optional<BlockPos> buildingCenter(Object building) {
        Object pos = ref(H_BUILDING_CENTER, building);
        return pos instanceof BlockPos p ? Optional.of(p) : Optional.empty();
    }

    private static int intOf(MethodHandle handle, Object receiver) {
        try {
            return (int) handle.invoke(receiver);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** The nearest {@code Village} to {@code pos} within {@code radius}, or null. */
    public static Object nearestVillage(ServerLevel level, BlockPos pos, int radius) {
        Object manager = villageManager(level);
        if (manager == null) {
            return null;
        }
        try {
            return unwrap(H_NEAREST_VILLAGE.invoke(manager, pos, radius));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Every {@code Village} in the level — {@code VillageManager} is {@code Iterable<Village>}. */
    public static List<Object> allVillages(ServerLevel level) {
        Object manager = villageManager(level);
        if (!(manager instanceof Iterable<?> iterable)) {
            return List.of();
        }
        try {
            List<Object> villages = new ArrayList<>();
            for (Object village : iterable) {
                villages.add(village);
            }
            return villages;
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** The village's stable MCA id, or {@link Integer#MIN_VALUE} when unavailable. */
    public static int villageId(Object village) {
        if (village == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return (int) H_VILLAGE_ID.invoke(village);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    public static String villageName(Object village) {
        return ref(H_VILLAGE_NAME, village) instanceof String s ? s : null;
    }

    /** The village centre as a {@link BlockPos}; MCA returns a {@link Vec3i}. Null when unavailable. */
    public static BlockPos villageCenter(Object village) {
        Object center = ref(H_VILLAGE_CENTER, village);
        if (center instanceof BlockPos pos) {
            return pos;
        }
        return center instanceof Vec3i vec ? new BlockPos(vec.getX(), vec.getY(), vec.getZ()) : null;
    }

    public static boolean isWithinBorder(Object village, BlockPos pos) {
        if (village == null) {
            return false;
        }
        try {
            return (boolean) H_VILLAGE_BORDER.invoke(village, pos, 0);
        } catch (Throwable t) {
            return false;
        }
    }

    public static List<UUID> villageResidentUuids(Object village) {
        return uuids(ref(H_VILLAGE_UUIDS, village));
    }

    /** Currently-loaded resident entities of a village. */
    public static List<Object> villageResidents(Object village, ServerLevel level) {
        return ref(H_VILLAGE_RESIDENTS, village, level) instanceof List<?> list
                ? new ArrayList<Object>(list) : List.of();
    }

    /**
     * True when {@code uuid} is on the village's resident roll. MCA removed {@code hasResident} in the
     * later 7.7 line, so this falls back to scanning the UUID stream, which exists in every version.
     */
    public static boolean villageHasResident(Object village, UUID uuid) {
        if (village == null || uuid == null) {
            return false;
        }
        if (HAS_HAS_RESIDENT) {
            return bool(H_VILLAGE_HAS_RESIDENT, village, uuid);
        }
        return villageResidentUuids(village).contains(uuid);
    }

    /** The village's shared storage buffer, used to measure famine. Empty when unavailable. */
    @SuppressWarnings("unchecked")
    public static List<ItemStack> villageStorage(Object village) {
        return ref(H_VILLAGE_STORAGE, village) instanceof List<?> list
                ? (List<ItemStack>) list : List.of();
    }
}
