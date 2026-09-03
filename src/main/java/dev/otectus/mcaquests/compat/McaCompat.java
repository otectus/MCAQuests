package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.mca.McaHandles;
import dev.otectus.mcaquests.state.PendingHeartsData;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The single point of contact with Minecraft Comes Alive: Reborn.
 *
 * <p><b>No MCA type appears anywhere in this file.</b> Every MCA class and member is reached through
 * {@link McaHandles}, which resolves them by name at runtime against whichever package root the
 * installed MCA uses. That is what lets one jar serve MCA's {@code forge.net.mca.*} layout and the
 * renamed {@code net.conczin.mca.*} layout of the later 7.7 line — previously this class imported the
 * former directly, so on a renamed build the first MCA reference threw
 * {@code NoClassDefFoundError} out of an entity-interaction handler and killed the server. See
 * {@link dev.otectus.mcaquests.compat.mca.McaBinding} for the probe, the manifest, and the
 * degradation contract; {@code NoMcaStaticLinkTest} fails the build if an MCA reference ever comes
 * back.
 *
 * <p>"Favor" in the spec maps to MCA "hearts", reached per-player through the villager's brain
 * memories. Keep <em>all</em> MCA access in this class so MCA API drift only ever requires edits
 * here and in the binding manifest.
 *
 * <h2>Failure policy</h2>
 *
 * <p>Every method below fails safe: on a non-MCA entity, absent MCA data, an unbound member, or any
 * throwable it returns its documented safe default and logs at DEBUG. <b>None of them ever propagate
 * an exception</b>, so a malformed family graph, a partially-loaded world, or an MCA version this
 * build has never seen can never crash the server. With MCA entirely unbound the mod is inert but
 * installed: no quest is offered, no villager menu opens, no hearts move, and nothing crashes.
 */
public final class McaCompat {

    /** Squared interaction/proximity radius shared by the interaction guard and "nearby" checks. */
    private static final double INTERACT_RANGE_SQR = 12.0D * 12.0D;

    /**
     * MCA's placeholder name for a player family-tree node it auto-creates before the player has set a
     * character name (MCA's player save data falls back to this when the entity is
     * offline/unresolvable). Treated as "no name set" so the Minecraft-username fallback still engages.
     */
    private static final String MCA_UNNAMED_PLACEHOLDER = "Unnamed Adventurer";

    /** MCA's age-state name for a grown villager, compared as a string so no MCA enum is named. */
    private static final String AGE_ADULT = "adult";

    private McaCompat() {
    }

    /**
     * True for an adult-or-child MCA human villager (not the zombie variant).
     *
     * <p>This is the hottest MCA call in the mod — it runs on every entity right-click, from two
     * {@code EntityInteract} handlers — and deliberately uses a constant-folded type check rather than
     * a bound method handle.
     */
    public static boolean isMcaVillager(Entity entity) {
        try {
            return McaHandles.isVillager(entity);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isMcaVillager failed; defaulting false", t);
            return false;
        }
    }

    public static UUID getVillagerUuid(Entity entity) {
        return entity.getUUID();
    }

    /**
     * The villager's display name. MCA's villager overrides vanilla {@code getDisplayName} as
     * {@code final}, so this needs no MCA binding at all and works identically for every entity.
     */
    public static Component getVillagerDisplayName(Entity entity) {
        try {
            return entity.getDisplayName();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("getVillagerDisplayName failed; defaulting empty", t);
            return Component.empty();
        }
    }

    /**
     * Normalises the villager's profession to a {@link ResourceLocation} (spec section 12). Tests
     * MCA's {@code VillagerLike} rather than the villager entity, so player-backed pseudo-villagers
     * resolve too.
     */
    public static Optional<ResourceLocation> getProfessionId(Entity entity) {
        try {
            return Optional.ofNullable(McaHandles.professionId(entity));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getProfessionId failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The villager's localised profession display name (e.g. "Farmer"), as MCA shows it. */
    public static Component getProfessionName(Entity entity) {
        try {
            Component text = McaHandles.professionText(entity);
            return text == null ? Component.empty() : text;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getProfessionName failed; defaulting empty", t);
            return Component.empty();
        }
    }

    /**
     * True when the villager is grown. Falls back to the vanilla {@link AgeableMob} baby flag when
     * MCA's age state is unavailable, so an unbound MCA degrades to "adult unless visibly a baby"
     * rather than to "nobody is an adult" (which would silently suppress every {@code adult_only}
     * quest).
     */
    public static boolean isAdult(Entity entity) {
        try {
            String age = McaHandles.ageStateName(entity);
            if (age != null) {
                return AGE_ADULT.equals(age);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isAdult failed; falling back to the vanilla age check", t);
        }
        if (entity instanceof AgeableMob mob) {
            return !mob.isBaby();
        }
        return true;
    }

    /**
     * Reads the player's current relationship hearts with this villager. Server-authoritative;
     * returns 0 for non-MCA entities. Safe to call on a synced client entity for display, but hearts
     * changes must only happen server-side (see {@link #addHearts}).
     */
    public static int getHearts(ServerPlayer player, Entity villager) {
        try {
            return McaHandles.hearts(villager, player);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getHearts failed; defaulting 0", t);
            return 0;
        }
    }

    /**
     * Adds relationship hearts with this villager via MCA's own brain reward path (the same one MCA's
     * gifting uses). <b>Server side only</b> — call after reward delivery.
     */
    public static void addHearts(ServerPlayer player, Entity villager, int amount) {
        if (amount == 0) {
            return;
        }
        try {
            McaHandles.rewardHearts(villager, player, amount);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA addHearts failed; ignoring", t);
        }
    }

    /**
     * Controls whether a quest-giver follows the player (MCA's {@code FOLLOW} move state). When
     * {@code follow} is false this only resets a villager that is <em>currently</em> following, so a
     * player's manual STAY/MOVE choice is left untouched. <b>Server side only.</b>
     */
    public static void setQuestGiverFollow(ServerPlayer player, Entity villager, boolean follow) {
        try {
            if (follow) {
                McaHandles.setFollow(villager, player);
            } else if (McaHandles.isFollowing(villager)) {
                McaHandles.setMove(villager, player);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA setQuestGiverFollow failed; ignoring", t);
        }
    }

    /**
     * Drives the villager to walk toward {@code dest} on its own — the NPC <em>leads</em> (the inverse of
     * {@link #setQuestGiverFollow}). Forces MCA's {@code MOVE} state (only when not already MOVE, to
     * avoid refreshing the brain every tick), then writes the vanilla {@link MemoryModuleType#WALK_TARGET}
     * memory that the villager's always-on move-to-walk-target behavior consumes to pathfind there.
     * Re-issue each poll — other brain behaviors can clear the walk target out from under us.
     * <b>Server side only.</b> No-op and fail-safe on a non-MCA entity or any error.
     */
    public static void leadVillagerTo(ServerPlayer player, Entity villager, BlockPos dest, double speed) {
        if (!isMcaVillager(villager) || !(villager instanceof Mob mob)) {
            return;
        }
        try {
            if (!McaHandles.isMoving(villager)) {
                McaHandles.setMove(villager, player);
            }
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(Vec3.atBottomCenterOf(dest), (float) speed, 1));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA leadVillagerTo failed; ignoring", t);
        }
    }

    /**
     * Pauses a leading villager: clears its {@link MemoryModuleType#WALK_TARGET} and stops the navigator so
     * it stands still when the player falls behind or the lead ends. Leaves the move state alone ({@code
     * MOVE} is the natural idle for a led villager). <b>Server side only.</b> Safe on a non-MCA entity or
     * any error.
     */
    public static void stopVillagerLeading(Entity villager) {
        if (!isMcaVillager(villager) || !(villager instanceof Mob mob)) {
            return;
        }
        try {
            mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            mob.getNavigation().stop();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA stopVillagerLeading failed; ignoring", t);
        }
    }

    /**
     * Pins a villager to its exact spot and makes it immune to all damage — used to "stage" an escortee so
     * it waits safely until the player reaches it. Uses vanilla {@code setNoAi} (a full AI/movement freeze,
     * not overridden by MCA) plus {@code setInvulnerable}; MCA's villager {@code hurt} is {@code final}
     * and delegates to {@code super.hurt}, so the invulnerable flag is honored. Re-assert each tick while
     * holding (both flags persist to entity NBT, so a still-active quest self-heals on reload). Always undo
     * with {@link #releaseVillagerHold}. <b>Server side only.</b> Safe on a non-MCA entity or any error.
     */
    public static void holdVillagerInPlace(Entity villager) {
        if (!isMcaVillager(villager) || !(villager instanceof Mob mob)) {
            return;
        }
        try {
            mob.setNoAi(true);
            mob.setInvulnerable(true);
            mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            mob.getNavigation().stop();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA holdVillagerInPlace failed; ignoring", t);
        }
    }

    /**
     * Releases a {@link #holdVillagerInPlace} hold: restores normal AI and vulnerability. Idempotent and
     * fail-safe; call it when the escort engages, completes, or the quest ends so a held villager is never
     * left frozen/invulnerable. <b>Server side only.</b>
     */
    public static void releaseVillagerHold(Entity villager) {
        if (!isMcaVillager(villager) || !(villager instanceof Mob mob)) {
            return;
        }
        try {
            mob.setInvulnerable(false);
            mob.setNoAi(false);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA releaseVillagerHold failed; ignoring", t);
        }
    }

    /** Basic guard for menu/turn-in actions: a living, nearby MCA villager in the player's level. */
    public static boolean canPlayerInteract(ServerPlayer player, Entity villager) {
        try {
            return isMcaVillager(villager)
                    && villager.isAlive()
                    && villager.level() == player.level()
                    && villager.distanceToSqr(player) <= INTERACT_RANGE_SQR;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA canPlayerInteract failed; defaulting false", t);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // v0.3.0 — MCA-aware condition data points (see docs/0.3.0-design.md §2).
    // ---------------------------------------------------------------------------------------------

    /** True when the villager is married specifically to this player. Safe default: {@code false}. */
    public static boolean isPlayerSpouse(ServerPlayer player, Entity villager) {
        try {
            return McaHandles.isMarriedTo(McaHandles.relationshipOf(villager), player.getUUID());
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isPlayerSpouse failed; defaulting false", t);
            return false;
        }
    }

    /** The villager's MCA relationship state (lowercased enum name). Safe default: {@code empty}. */
    public static Optional<String> getRelationshipState(Entity villager) {
        try {
            return Optional.ofNullable(
                    McaHandles.relationshipStateName(McaHandles.relationshipOf(villager)));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getRelationshipState failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * True when the villager is the given {@code relation} <em>to the player</em> in MCA's family
     * tree. {@code relation} is one of {@code any}/{@code spouse}/{@code parent}/{@code child}/
     * {@code sibling}/{@code grandparent}. Safe default: {@code false}.
     *
     * <p><b>Not the same question as {@link #relativeCandidates}.</b> That one asks who the giver's
     * relatives are; this one asks how the giver is related to the <em>player</em>, and the relation runs
     * in the opposite direction. They must never be folded together, and a quest gated on this one has
     * said nothing at all about whether the giver has a findable relative.
     *
     * <p>Direction note (verified against MCA bytecode): a node's {@code isParent(uuid)} means
     * "{@code uuid} is one of this node's parents". So the giver being the player's <em>child</em>
     * means the player is one of the giver's parents.
     */
    public static boolean isFamilyOfPlayer(ServerPlayer player, Entity villager, String relation) {
        try {
            Object relationship = McaHandles.relationshipOf(villager);
            Object node = McaHandles.familyEntry(relationship);
            if (node == null) {
                return false;
            }
            UUID p = player.getUUID();
            return switch (relation) {
                case "any" -> McaHandles.nodeIsRelative(node, p);
                // Marriage is not a family-tree edge in MCA, so it needs the relationship, not the node.
                // Without this branch "spouse" fell through to false, which is why the vocabulary this
                // condition accepts had to omit it while related_villager_status accepted it.
                case "spouse" -> McaHandles.isMarriedTo(relationship, p);
                case "child" -> McaHandles.nodeIsParent(node, p);            // player is giver's parent
                case "parent" -> McaHandles.nodeChildren(node).contains(p);  // player is giver's child
                case "sibling" -> McaHandles.nodeSiblings(node).contains(p);
                case "grandparent" -> {                                      // player is giver's grandchild
                    Object tree = McaHandles.familyTree(relationship);
                    boolean found = false;
                    for (UUID childUuid : McaHandles.nodeChildren(node)) {
                        Object child = McaHandles.node(tree, childUuid);
                        if (child != null && McaHandles.nodeChildren(child).contains(p)) {
                            found = true;
                            break;
                        }
                    }
                    yield found;
                }
                default -> false;
            };
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isFamilyOfPlayer(relation={}) failed; defaulting false", relation, t);
            return false;
        }
    }

    /** The villager's MCA age state (lowercased enum name). Safe default: {@code empty}. */
    public static Optional<String> getAgeStateName(Entity villager) {
        try {
            return Optional.ofNullable(McaHandles.ageStateName(villager));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getAgeStateName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The villager's MCA personality (lowercased enum name). Safe default: {@code empty}. */
    public static Optional<String> getPersonalityName(Entity villager) {
        try {
            return Optional.ofNullable(McaHandles.personalityName(villager));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getPersonalityName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The villager's current MCA mood value. Safe default: {@code empty}. */
    public static OptionalInt getMoodValue(Entity villager) {
        try {
            int mood = McaHandles.moodValue(villager);
            return mood == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(mood);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getMoodValue failed; defaulting empty", t);
            return OptionalInt.empty();
        }
    }

    /** The villager's current MCA mood name (lowercased). Safe default: {@code empty}. */
    public static Optional<String> getMoodName(Entity villager) {
        try {
            return Optional.ofNullable(McaHandles.moodName(villager));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getMoodName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** True when the villager belongs to a home village. Safe default: {@code false}. */
    public static boolean hasHomeVillage(Entity villager) {
        try {
            return McaHandles.homeVillage(villager) != null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA hasHomeVillage failed; defaulting false", t);
            return false;
        }
    }

    /** True when the villager has an assigned home position. Safe default: {@code false}. */
    public static boolean hasHome(Entity villager) {
        try {
            return McaHandles.homePos(villager) != null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA hasHome failed; defaulting false", t);
            return false;
        }
    }

    /**
     * The villager's health as a fraction of its max (0..1). Works on any living entity, so this is
     * MCA-agnostic. Safe default: {@code empty}.
     */
    public static OptionalDouble getHealthFraction(Entity villager) {
        if (villager instanceof LivingEntity living) {
            try {
                float max = living.getMaxHealth();
                if (max <= 0f) {
                    return OptionalDouble.empty();
                }
                return OptionalDouble.of(living.getHealth() / max);
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("getHealthFraction failed; defaulting empty", t);
            }
        }
        return OptionalDouble.empty();
    }

    /** True when the villager is currently zombie-infected (infection progress &gt; 0). Safe default: {@code false}. */
    public static boolean isInfected(Entity villager) {
        return getInfectionProgress(villager) > 0f;
    }

    /** The villager's zombie-infection progress (0..1). Safe default: {@code 0f}. */
    public static float getInfectionProgress(Entity villager) {
        try {
            return McaHandles.infectionProgress(villager);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getInfectionProgress failed; defaulting 0", t);
            return 0f;
        }
    }

    /**
     * Every relative of {@code giver} of the given {@code relation}, described richly enough that any
     * caller can decide whether they are a person a quest may be about.
     *
     * <p><b>This is the single family predicate.</b> The condition gate, the offer-time resolvability
     * check, the accept-time binder, {@code VillagerTarget.matches} and the display name all filter this
     * one list. They used to walk the tree three different ways: the gate asked "is there a sibling in
     * this village?" while the target asked for "the first sibling in list order" with no status filter
     * at all, so one sibling could satisfy the gate while a different one — dead, invented, or nowhere in
     * the world — was handed to the objective. They can no longer disagree, because there is only one
     * question being asked.
     *
     * <p>The village rolls are read <em>once for the whole list</em> rather than once per candidate, so
     * asking about a giver's four siblings no longer walks every village in the level four times.
     *
     * <p>Safe default: an empty list — which makes the content that named this relative ineligible
     * rather than accidentally satisfied.
     */
    public static List<RelativeCandidate> relativeCandidates(ServerLevel level, Entity giver, String relation) {
        try {
            Object relationship = McaHandles.relationshipOf(giver);
            Object node = McaHandles.familyEntry(relationship);
            if (node == null) {
                return List.of();
            }
            List<UUID> ids = relativeIds(node, relation, giver.getUUID());
            if (ids.isEmpty()) {
                return List.of();
            }
            Object tree = McaHandles.familyTree(relationship);
            Set<UUID> homeVillage = homeVillageResidents(giver);
            Set<UUID> anywhere = residentsAnywhere(level, ids);
            List<RelativeCandidate> candidates = new ArrayList<>(ids.size());
            for (UUID uuid : ids) {
                candidates.add(describeRelative(level, giver, tree, uuid, relation, homeVillage, anywhere));
            }
            return List.copyOf(candidates);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA relativeCandidates(relation={}) failed; defaulting empty", relation, t);
            return List.of();
        }
    }

    /** Reads one relative's node into the JDK-only shape the rest of the mod reasons about. */
    private static RelativeCandidate describeRelative(ServerLevel level, Entity giver, Object tree, UUID uuid,
                                                      String relation, Set<UUID> homeVillage, Set<UUID> anywhere) {
        Object entry = McaHandles.node(tree, uuid);
        Entity entity = level.getEntity(uuid);
        boolean embodied = entity != null;
        boolean loaded = embodied && entity.isAlive();
        return new RelativeCandidate(
                uuid,
                relation,
                McaHandles.nodeName(entry),
                entry != null,
                McaHandles.nodeDeceased(entry),
                McaHandles.nodeProbablyGenerated(entry),
                McaHandles.nodeIsPlayer(entry),
                embodied,
                loaded,
                loaded && entity.distanceToSqr(giver) <= INTERACT_RANGE_SQR,
                homeVillage.contains(uuid),
                anywhere.contains(uuid),
                canMaterialise(level, entry, uuid),
                loaded && isInfected(entity));
    }

    /** The giver's own village roll, or an empty set when they have no home village / on any error. */
    private static Set<UUID> homeVillageResidents(Entity giver) {
        Object village = McaHandles.homeVillage(giver);
        // Copied into a set because every candidate asks it a membership question and a village roll is
        // a list; on a large village that is the difference between one hash lookup and a linear scan.
        return village == null ? Set.of() : new HashSet<>(McaHandles.villageResidentUuids(village));
    }

    /**
     * Which of {@code ids} appear on any village's resident roll, in one pass over the level's villages.
     *
     * <p>The per-candidate form of this ({@link #isVillageResidentAnywhere}) is what made the old
     * {@code missing} check quadratic: every relative of every relation of every candidate quest walked
     * every village. Answering for the whole set at once costs one walk.
     */
    private static Set<UUID> residentsAnywhere(ServerLevel level, List<UUID> ids) {
        Set<UUID> found = new HashSet<>();
        try {
            for (Object village : McaHandles.allVillages(level)) {
                List<UUID> residents = McaHandles.villageResidentUuids(village);
                for (UUID uuid : ids) {
                    if (residents.contains(uuid)) {
                        found.add(uuid);
                    }
                }
                if (found.size() == ids.size()) {
                    break;
                }
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA residentsAnywhere failed; defaulting to none on a roll", t);
        }
        return found;
    }

    /**
     * Whether {@link #materializeRelative} would agree to bring this relative into the world: a known
     * node, not deceased, not a player, not a filler ancestor MCA invented, and with no body anywhere
     * already.
     *
     * <p>Shared with {@code materializeRelative} itself rather than restated, so "the mod says they can
     * be found" and "the mod can actually produce them" cannot drift apart. They were previously two
     * hand-maintained copies of the same list.
     */
    private static boolean canMaterialise(ServerLevel level, Object node, UUID uuid) {
        return node != null
                && !McaHandles.nodeDeceased(node)
                && !McaHandles.nodeIsPlayer(node)
                && !McaHandles.nodeProbablyGenerated(node)
                && level.getEntity(uuid) == null;
    }

    /**
     * The raw relation walk: every relative id of {@code relation}, self / nil / duplicates removed.
     *
     * <p>{@code grandparent} is a two-hop walk (each parent's parents). {@code any} deliberately does
     * <em>not</em> include grandparents: it means "immediate family", and widening it would change which
     * villager every existing {@code "relation": "any"} quest resolves to.
     *
     * <p>Order is spouse, then parents, then children, then siblings — the order every existing
     * {@code any} quest already resolves in, and so one that must not change. Siblings alone are sorted
     * by id, because MCA hands them back as a {@link Set} whose iteration order was never specified, so
     * which sibling a quest picked could differ between two passes over identical data.
     */
    private static List<UUID> relativeIds(Object node, String relation, UUID self) {
        List<UUID> relatives = new ArrayList<>();
        if (relation.equals("spouse") || relation.equals("any")) {
            UUID partner = McaHandles.nodePartner(node);
            if (partner != null && !partner.equals(Util.NIL_UUID)) {
                relatives.add(partner);
            }
        }
        if (relation.equals("parent") || relation.equals("any")) {
            relatives.addAll(McaHandles.nodeParents(node));
        }
        if (relation.equals("child") || relation.equals("any")) {
            relatives.addAll(McaHandles.nodeChildren(node));
        }
        if (relation.equals("sibling") || relation.equals("any")) {
            List<UUID> siblings = new ArrayList<>(McaHandles.nodeSiblings(node));
            siblings.sort(Comparator.nullsLast(Comparator.naturalOrder()));
            relatives.addAll(siblings);
        }
        if (relation.equals("grandparent")) {
            for (Object parentNode : McaHandles.nodeParentNodes(node)) {
                relatives.addAll(McaHandles.nodeParents(parentNode));
            }
        }
        List<UUID> cleaned = new ArrayList<>();
        for (UUID uuid : relatives) {
            if (uuid != null && !uuid.equals(Util.NIL_UUID) && !uuid.equals(self) && !cleaned.contains(uuid)) {
                cleaned.add(uuid);
            }
        }
        return cleaned;
    }

    /**
     * One named villager, described the same way a relative is, without needing to know how (or whether)
     * they are related to anybody.
     *
     * <p>This is the read behind "the person this quest bound has been lost". An accepted quest stores a
     * target UUID; months later the giver may be unloaded, the relation may be irrelevant, and the only
     * question worth asking is "is this villager still someone the player can go and find?". Reads the
     * <em>level's</em> family tree rather than the giver's, so it answers even when nobody is loaded.
     *
     * <p>Safe default: {@code empty}, meaning "cannot tell" — which callers must treat as "say nothing",
     * never as "they are gone". Accusing a quest of losing its target because MCA was briefly unreadable
     * would be worse than staying quiet.
     */
    public static Optional<RelativeCandidate> describeVillager(ServerLevel level, @Nullable Entity giver,
                                                               UUID uuid) {
        try {
            Object tree = McaHandles.familyTree(level);
            Object node = McaHandles.node(tree, uuid);
            if (node == null) {
                return Optional.empty();
            }
            Entity entity = level.getEntity(uuid);
            boolean embodied = entity != null;
            boolean loaded = embodied && entity.isAlive();
            Set<UUID> homeVillage = giver == null ? Set.of() : homeVillageResidents(giver);
            boolean anywhere = isVillageResidentAnywhere(level, uuid);
            return Optional.of(new RelativeCandidate(
                    uuid,
                    "bound",
                    McaHandles.nodeName(node),
                    true,
                    McaHandles.nodeDeceased(node),
                    McaHandles.nodeProbablyGenerated(node),
                    McaHandles.nodeIsPlayer(node),
                    embodied,
                    loaded,
                    loaded && giver != null && entity.distanceToSqr(giver) <= INTERACT_RANGE_SQR,
                    homeVillage.contains(uuid),
                    anywhere,
                    canMaterialise(level, node, uuid),
                    loaded && isInfected(entity)));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA describeVillager failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * True when the giver has at least one relative of {@code relation} ({@code any}/{@code spouse}/
     * {@code parent}/{@code child}/{@code sibling}/{@code grandparent}) whose {@code status}
     * ({@code alive}/{@code reachable}/{@code nearby}/{@code missing}/{@code dead}/{@code same_village}/
     * {@code any_known}) matches. Touches MCA's persistent family tree, so it resolves relatives even
     * when they are not currently loaded. Safe default: {@code false}.
     *
     * <p>This is a filter over {@link #relativeCandidates}, which is also what an objective's target
     * selects from — so a quest gated on "{@code relation} exists with {@code status}" and a target
     * requiring that same status genuinely cannot disagree about who is in scope. The previous version of
     * this javadoc claimed that guarantee while the code re-derived its own list inline, skipping even
     * the self/nil/duplicate cleaning; {@code GateTargetAgreementTest} now holds it to the claim.
     */
    public static boolean relativesWithStatus(ServerLevel level, Entity giver, String relation, String status) {
        for (RelativeCandidate candidate : relativeCandidates(level, giver, relation)) {
            if (candidate.matches(status)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when {@code villager} has at least one missing relative of any tracked relation
     * (spouse/parent/child/sibling) — the village-scoped signal for the {@code missing_kin} situation
     * trigger (0.8.0). Safe default: {@code false}.
     *
     * <p>{@code any} is exactly that set, so this is now one tree walk rather than the four it used to be.
     */
    public static boolean hasMissingRelative(ServerLevel level, Entity villager) {
        return relativesWithStatus(level, villager, "any", "missing");
    }

    // ---------------------------------------------------------------------------------------------
    // v0.4.0 — Village/family identity for community projects (see docs/0.4.0-design.md §2/§4).
    //
    // MCA exposes a stable village id, a center anchor, a border test, and the resident set. We
    // surface only primitives (OptionalInt / BlockPos / UUID / Set) so no MCA type escapes this class.
    // Every method fails safe to a documented default and never throws.
    // ---------------------------------------------------------------------------------------------

    /** The id of the villager's MCA home village, or empty when it has none / on any error. */
    public static OptionalInt getHomeVillageId(Entity villager) {
        try {
            return villageIdOf(McaHandles.homeVillage(villager));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getHomeVillageId failed; defaulting empty", t);
            return OptionalInt.empty();
        }
    }

    /** The villager's MCA home village display name. Safe default: {@code empty}. */
    public static Optional<String> getHomeVillageName(Entity villager) {
        try {
            return Optional.ofNullable(McaHandles.villageName(McaHandles.homeVillage(villager)));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getHomeVillageName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The center (anchor) of the villager's MCA home village. Safe default: {@code empty}. */
    public static Optional<BlockPos> getHomeVillageCenter(Entity villager) {
        try {
            return Optional.ofNullable(McaHandles.villageCenter(McaHandles.homeVillage(villager)));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getHomeVillageCenter failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The display name of a village resolved by id. Safe default: {@code empty}. */
    public static Optional<String> villageName(ServerLevel level, int villageId) {
        try {
            return Optional.ofNullable(McaHandles.villageName(McaHandles.village(level, villageId)));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The center (anchor) of a village resolved by id. Safe default: {@code empty}. */
    public static Optional<BlockPos> villageCenter(ServerLevel level, int villageId) {
        try {
            return Optional.ofNullable(McaHandles.villageCenter(McaHandles.village(level, villageId)));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageCenter failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The id of the nearest village to {@code pos} within {@code radius} blocks. Safe default: {@code empty}. */
    public static OptionalInt findNearestVillageId(ServerLevel level, BlockPos pos, int radius) {
        try {
            return villageIdOf(McaHandles.nearestVillage(level, pos, radius));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA findNearestVillageId failed; defaulting empty", t);
            return OptionalInt.empty();
        }
    }

    /**
     * The nearest village to {@code pos} within {@code radius}, skipping {@code excluded} (spec §5.4).
     *
     * <p>Enumerates rather than delegating to MCA's own nearest-village search, because that search
     * has no notion of "but not that one" and would keep answering with the giver's own village — the
     * one case a route quest must never point at. Ties break on the lower village id so the same world
     * always yields the same road.
     *
     * <p>Safe default: {@code empty}, which makes the quest ineligible rather than sending the player
     * to nowhere.
     */
    public static OptionalInt findNearestVillageIdExcluding(ServerLevel level, BlockPos pos, int radius,
                                                            OptionalInt excluded) {
        try {
            double limit = (double) radius * radius;
            int bestId = -1;
            double bestDistance = Double.MAX_VALUE;
            for (Object village : McaHandles.allVillages(level)) {
                OptionalInt id = villageIdOf(village);
                if (id.isEmpty() || (excluded.isPresent() && id.getAsInt() == excluded.getAsInt())) {
                    continue;
                }
                BlockPos center = McaHandles.villageCenter(village);
                if (center == null) {
                    continue;
                }
                double distance = center.distSqr(pos);
                if (distance > limit) {
                    continue;
                }
                if (distance < bestDistance || (distance == bestDistance && id.getAsInt() < bestId)) {
                    bestId = id.getAsInt();
                    bestDistance = distance;
                }
            }
            return bestId < 0 ? OptionalInt.empty() : OptionalInt.of(bestId);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA findNearestVillageIdExcluding failed; defaulting empty", t);
            return OptionalInt.empty();
        }
    }

    /** True when {@code pos} lies within the border of the village with {@code villageId}. Safe default: {@code false}. */
    public static boolean isWithinVillage(ServerLevel level, int villageId, BlockPos pos) {
        try {
            return McaHandles.isWithinBorder(McaHandles.village(level, villageId), pos);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isWithinVillage failed; defaulting false", t);
            return false;
        }
    }

    /** True when the village with {@code villageId} still exists. Safe default: {@code false}. */
    public static boolean villageExists(ServerLevel level, int villageId) {
        try {
            return McaHandles.village(level, villageId) != null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageExists failed; defaulting false", t);
            return false;
        }
    }

    /** True when {@code uuid} is a resident of the village with {@code villageId}. Safe default: {@code false}. */
    public static boolean villageContains(ServerLevel level, int villageId, UUID uuid) {
        try {
            return McaHandles.villageHasResident(McaHandles.village(level, villageId), uuid);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageContains failed; defaulting false", t);
            return false;
        }
    }

    /**
     * The village {@code uuid} is a resident of, by scanning every village in {@code level}.
     *
     * <p>The fallback for a reward that needs the giver's village on a quest accepted before 1.5.1 froze
     * one, when the giver itself is not loaded: {@link #getHomeVillageId} needs the entity, and a quest
     * completed in the field routinely has none. Only ever reached on that path, so the scan runs once
     * per such turn-in rather than per tick. Safe default: {@code empty} (no village, no award).
     */
    public static OptionalInt findVillageOfResident(ServerLevel level, UUID uuid) {
        try {
            for (Object village : McaHandles.allVillages(level)) {
                OptionalInt id = villageIdOf(village);
                if (id.isPresent() && McaHandles.villageHasResident(village, uuid)) {
                    return id;
                }
            }
            return OptionalInt.empty();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA findVillageOfResident failed; defaulting empty", t);
            return OptionalInt.empty();
        }
    }

    /**
     * The count of edible items currently banked in the village's MCA storage buffer — the famine
     * measure for the {@code low_food} situation trigger (0.8.0). Empty when the village does not
     * resolve or MCA storage is unavailable (the trigger then never fires). Safe-fail.
     */
    public static OptionalInt getVillageFoodCount(ServerLevel level, int villageId) {
        try {
            Object village = McaHandles.village(level, villageId);
            if (village == null) {
                return OptionalInt.empty();
            }
            int food = 0;
            for (ItemStack stack : McaHandles.villageStorage(village)) {
                if (stack != null && stack.isEdible()) {
                    food += stack.getCount();
                }
            }
            return OptionalInt.of(food);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getVillageFoodCount failed; defaulting empty", t);
            return OptionalInt.empty();
        }
    }

    /**
     * True when a vanilla raid is currently active at {@code pos} (used to detect the {@code raid}
     * situation near a village center). Not MCA-specific, but kept here with the other village helpers.
     * Safe default: {@code false}.
     */
    public static boolean isRaidActive(ServerLevel level, BlockPos pos) {
        try {
            return level.getRaidAt(pos) != null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("isRaidActive failed; defaulting false", t);
            return false;
        }
    }

    /** The currently-loaded resident villager entities of the village with {@code villageId}. Safe default: empty list. */
    public static List<Entity> loadedVillageResidents(ServerLevel level, int villageId) {
        try {
            Object village = McaHandles.village(level, villageId);
            List<Entity> residents = new ArrayList<>();
            for (Object resident : McaHandles.villageResidents(village, level)) {
                if (resident instanceof Entity entity) {
                    residents.add(entity);
                }
            }
            return residents;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA loadedVillageResidents failed; defaulting empty", t);
            return new ArrayList<>();
        }
    }

    /** The resident UUIDs of the village with {@code villageId}. Safe default: empty set. */
    public static Set<UUID> villageResidentUuids(ServerLevel level, int villageId) {
        try {
            return new HashSet<>(McaHandles.villageResidentUuids(McaHandles.village(level, villageId)));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageResidentUuids failed; defaulting empty", t);
            return new HashSet<>();
        }
    }

    /**
     * Records hearts owed to a villager who is not loaded right now, to be applied when it and
     * {@code player} are next both available.
     *
     * <p>This used to hand the amount to MCA's own {@code Village#pushHearts(UUID,int)} queue, but MCA
     * deleted that queue in the 7.7 line, so MCA: Quests keeps its own ledger
     * ({@link PendingHeartsData}) and uses it on every MCA version. The ledger also fixes a
     * long-standing inconsistency: MCA's queue was village-wide and player-agnostic while the
     * loaded-villager path beside it ({@link #addHearts}) has always been per-player, so the same grant
     * meant different things depending on whether a chunk happened to be loaded. It now means the same
     * thing either way, which is why this takes the player MCA's API did not.
     *
     * <b>Server side only.</b>
     */
    public static void queueHeartsForLater(ServerLevel level, UUID villagerUuid, ServerPlayer player, int amount) {
        if (amount == 0 || villagerUuid == null || player == null) {
            return;
        }
        try {
            if (level.getServer() == null) {
                return;
            }
            PendingHeartsData.get(level.getServer()).queue(villagerUuid, player.getUUID(), amount);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("Queueing pending hearts failed; ignoring", t);
        }
    }

    /**
     * Grants hearts to a villager identified by UUID, whether or not it is currently loaded: applied
     * immediately when the villager is in the world, otherwise recorded in {@link PendingHeartsData}
     * and paid when it next loads.
     *
     * <p>Every hearts-granting caller goes through here, so "loaded" and "unloaded" residents of the
     * same village always receive the same reward. <b>Server side only.</b>
     */
    public static void awardHearts(ServerLevel level, UUID villagerUuid, ServerPlayer player, int amount) {
        if (amount == 0 || villagerUuid == null || player == null) {
            return;
        }
        try {
            Entity villager = level.getEntity(villagerUuid);
            if (villager != null && villager.isAlive() && isMcaVillager(villager)) {
                addHearts(player, villager, amount);
            } else {
                queueHeartsForLater(level, villagerUuid, player, amount);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA awardHearts failed; ignoring", t);
        }
    }

    /**
     * A deterministic, stable identity for the villager's family <em>lineage</em>: the minimum UUID over
     * {@code {self} ∪ transitive ancestors} (bounded walk up the parent chain). Order-independent and
     * stable across relative deaths (deceased ancestors remain in MCA's family tree). This groups a
     * lineage, not a household. Safe default: {@code empty} (no family entry).
     */
    public static Optional<UUID> getFamilyRootId(Entity villager) {
        try {
            Object relationship = McaHandles.relationshipOf(villager);
            Object node = McaHandles.familyEntry(relationship);
            if (node == null) {
                return Optional.empty();
            }
            Object tree = McaHandles.familyTree(relationship);
            UUID self = McaHandles.nodeId(node);
            if (self == null) {
                return Optional.empty();
            }
            UUID min = self;
            Set<UUID> visited = new HashSet<>();
            visited.add(self);
            List<UUID> frontier = new ArrayList<>(List.of(self));
            for (int depth = 0; depth < 8 && !frontier.isEmpty(); depth++) {
                List<UUID> next = new ArrayList<>();
                for (UUID id : frontier) {
                    Object entry = McaHandles.node(tree, id);
                    if (entry == null) {
                        continue;
                    }
                    for (UUID parent : McaHandles.nodeParents(entry)) {
                        if (visited.add(parent)) {
                            next.add(parent);
                        }
                    }
                }
                for (UUID id : next) {
                    if (id.compareTo(min) < 0) {
                        min = id;
                    }
                }
                frontier = next;
            }
            return Optional.of(min);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getFamilyRootId failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Location anchors & relative resolution for the NPC/village objective types.
    //
    // MCA's residency exposes the villager's assigned home and workplace, and its family tree lets us
    // pick a concrete relative of the giver. We surface only primitives (BlockPos / UUID) so no MCA
    // type escapes this class. Every method fails safe to a documented default and never throws.
    // ---------------------------------------------------------------------------------------------

    /** The villager's assigned home/bed position (MCA residency). Safe default: {@code empty}. */
    public static Optional<BlockPos> getHomePos(Entity villager) {
        try {
            GlobalPos home = McaHandles.homePos(villager);
            return home == null ? Optional.empty() : Optional.of(home.pos());
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getHomePos failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The villager's workstation/job-site position (MCA residency). Safe default: {@code empty}. */
    public static Optional<BlockPos> getWorkstationPos(Entity villager) {
        try {
            BlockPos pos = McaHandles.workplace(villager);
            // MCA returns BlockPos.ZERO (and occasionally null) when no workplace is assigned.
            if (pos != null && !pos.equals(BlockPos.ZERO)) {
                return Optional.of(pos);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getWorkstationPos failed; defaulting empty", t);
        }
        return Optional.empty();
    }

    /**
     * Every relative of {@code giver} of the given {@code relation} ({@code any}/{@code spouse}/
     * {@code parent}/{@code child}/{@code sibling}/{@code grandparent}), by UUID. Walks MCA's
     * persistent family tree, so it lists relatives even when they are unloaded. Self and the MCA nil
     * UUID are filtered out. Safe default: empty list.
     *
     * <p>Unfiltered by design — this is "who is related", not "who may a quest be about". Anything
     * deciding the latter must go through {@link #relativeCandidates} and a status, or it will bind a
     * dead relative, a player, or one of the two deceased parents MCA invents for every villager it
     * spawns. Kept public because add-ons use it.
     */
    public static List<UUID> giverRelativeUuids(ServerLevel level, Entity giver, String relation) {
        try {
            Object node = McaHandles.familyEntry(McaHandles.relationshipOf(giver));
            return node == null ? List.of() : List.copyOf(relativeIds(node, relation, giver.getUUID()));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA giverRelativeUuids(relation={}) failed; defaulting empty", relation, t);
            return List.of();
        }
    }

    /**
     * A concrete relative of {@code giver} of the given {@code relation}, preferring one that is
     * currently loaded so the caller can act on the entity. Applies no filter at all, so it can return a
     * relative who is dead, invented or nowhere in the world; prefer
     * {@link #findGiverRelative(ServerLevel, Entity, String, String)}. Safe default: {@code empty}.
     */
    public static Optional<UUID> findGiverRelative(ServerLevel level, Entity giver, String relation) {
        return findGiverRelative(level, giver, relation, "any_known");
    }

    /**
     * A concrete relative of {@code giver} of the given {@code relation} who satisfies {@code require},
     * preferring one that is currently loaded so the caller can act on the entity.
     *
     * <p>This is the selection every villager target, the accept-time binder and the display name share.
     * The {@code require} filter is the difference between "a sibling" and "a sibling this quest can
     * honestly name": without it, the first entry in the walk wins even when they died last week and MCA
     * merely never took them off the village roll.
     *
     * <p>Safe default: {@code empty}, which makes the content that named this relative unofferable rather
     * than bound to a phantom.
     */
    public static Optional<UUID> findGiverRelative(ServerLevel level, Entity giver, String relation,
                                                   String require) {
        UUID firstKnown = null;
        for (RelativeCandidate candidate : relativeCandidates(level, giver, relation)) {
            if (!candidate.matches(require)) {
                continue;
            }
            if (candidate.loaded()) {
                return Optional.of(candidate.uuid()); // prefer a loaded relative
            }
            if (firstKnown == null) {
                firstKnown = candidate.uuid();
            }
        }
        return Optional.ofNullable(firstKnown);
    }

    /**
     * Brings a relative who exists only in MCA's persistent family tree into the world at
     * {@code pos}, and returns them. This is what makes a "missing kin" quest referable: MCA defines
     * <em>missing</em> as "has a family-tree entry, is not deceased, and has no entity anywhere", so until
     * the villager is materialised there is literally nobody for a quest to target, name, or highlight.
     *
     * <p>Two ordering rules make the villager <em>the same person</em> rather than a look-alike, both
     * verified against MCA's bytecode:
     * <ul>
     *   <li><b>{@code setUUID} first</b>, before anything touches the family tree and before
     *   {@code addFreshEntity}. MCA's {@code setName} writes through the entity's relationship family
     *   entry — that is a get-or-create keyed on the entity's <em>current</em> UUID — so naming it
     *   first would create a junk node under the throwaway id, and the entity index keys on the UUID it
     *   had when it was added.</li>
     *   <li><b>{@code initialize(MobSpawnType)}, never {@code finalizeSpawn}.</b> MCA's
     *   {@code finalizeSpawn} invents two {@code UUID.randomUUID()} deceased parents whenever the node's
     *   father/mother are not valid. For a relative we are restoring, that would rewrite real
     *   genealogy. {@code initialize} gives the genetics, traits, skin and brain setup without it.</li>
     * </ul>
     * {@code moveTo} precedes {@code initialize} because MCA's genetics randomiser reads the biome at the
     * entity's position.
     *
     * <p>Refuses to act when the node is unknown, deceased, a player, a filler ancestor MCA generated, or
     * already embodied somewhere in the world, so calling it repeatedly can never produce two of the same
     * villager. Safe default: {@code empty} — a caller that gets nothing back should simply pause and
     * retry, never fail the quest.
     */
    public static Optional<Entity> materializeRelative(ServerLevel level, UUID relativeUuid, BlockPos pos) {
        try {
            if (level.getEntity(relativeUuid) != null) {
                return Optional.empty(); // already in the world — never spawn a second copy
            }
            Object tree = McaHandles.familyTree(level);
            Object node = McaHandles.node(tree, relativeUuid);
            // The same predicate RelativeCandidate.materialisable() reports, so what the mod tells a
            // datapack it can find and what it will actually produce can never drift apart.
            if (!canMaterialise(level, node, relativeUuid)) {
                return Optional.empty();
            }
            Object gender = McaHandles.nodeBinaryGender(node);
            if (gender == null) {
                gender = McaHandles.randomBinaryGender();
            }
            EntityType<? extends Entity> type = McaHandles.villagerTypeOf(gender);
            if (type == null) {
                return Optional.empty();
            }
            Entity villager = type.create(level);
            if (villager == null || !isMcaVillager(villager)) {
                return Optional.empty();
            }
            UUID scratch = villager.getUUID();
            villager.setUUID(relativeUuid); // must precede initialize/setName/addFreshEntity — see above
            villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360f, 0f);
            McaHandles.initialize(villager, MobSpawnType.EVENT);
            McaHandles.setName(villager, McaHandles.nodeName(node));
            McaHandles.setProfession(villager, McaHandles.nodeProfession(node));
            if (!level.addFreshEntity(villager)) {
                return Optional.empty();
            }
            if (!scratch.equals(relativeUuid)) {
                McaHandles.removeNode(tree, scratch); // drop any node MCA created under the throwaway id
            }
            return Optional.of(villager);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA materializeRelative failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * The last place a villager is known to live, as a dimension-qualified position: the centre of
     * whichever MCA village has them on its resident roll. Works for a villager who is <em>not loaded</em>,
     * which is exactly when a "which way do I walk?" hint is worth having — a loaded villager can just be
     * asked where it is. Safe default: {@code empty}.
     */
    public static Optional<GlobalPos> getRelativeHome(ServerLevel level, UUID uuid) {
        try {
            for (Object village : McaHandles.allVillages(level)) {
                if (McaHandles.villageResidentUuids(village).contains(uuid)) {
                    BlockPos center = McaHandles.villageCenter(village);
                    return center == null ? Optional.empty()
                            : Optional.of(GlobalPos.of(level.dimension(), center));
                }
            }
            return Optional.empty();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getRelativeHome failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * True when {@code uuid} is on the resident roll of any MCA village. Distinguishes a relative who is
     * alive but merely <em>unloaded</em> from one who has genuinely vanished — {@code ServerLevel#getEntity}
     * only sees loaded entities, so without this a villager standing in an unloaded village reads as
     * "missing" and could be duplicated. Safe default: {@code false}.
     */
    public static boolean isVillageResidentAnywhere(ServerLevel level, UUID uuid) {
        try {
            for (Object village : McaHandles.allVillages(level)) {
                if (McaHandles.villageResidentUuids(village).contains(uuid)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isVillageResidentAnywhere failed; defaulting false", t);
            return false;
        }
    }

    /**
     * The display name of a giver's relative by UUID, read from MCA's persistent family tree so it
     * resolves <em>even when the relative is not currently loaded</em>. Lets a quest objective name the
     * villager the player must find. Safe default: {@code empty}.
     */
    public static Optional<String> getRelativeDisplayName(Entity giver, UUID relativeUuid) {
        try {
            Object tree = McaHandles.familyTree(McaHandles.relationshipOf(giver));
            return Optional.ofNullable(McaHandles.nodeName(McaHandles.node(tree, relativeUuid)));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getRelativeDisplayName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * The player's chosen MCA character name (set in MCA's character-creation screen), read from their
     * node in MCA's persistent family tree via its player save data. Empty when MCA is absent, the
     * player has not set a name, or it is blank. Safe default: {@code empty}.
     */
    public static Optional<String> getMcaPlayerName(ServerPlayer player) {
        try {
            Object node = McaHandles.familyEntry(McaHandles.playerSave(player));
            return Optional.ofNullable(McaHandles.nodeName(node))
                    .filter(name -> !name.equals(MCA_UNNAMED_PLACEHOLDER));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getMcaPlayerName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * The player's MCA character name with their Minecraft username as a safe fallback (when MCA is absent
     * or no name has been set). Never blank or null — suitable for direct display.
     */
    public static String getPlayerName(ServerPlayer player) {
        return getMcaPlayerName(player).orElseGet(() -> player.getGameProfile().getName());
    }

    // ---------------------------------------------------------------------------------------------
    // §11.5 — marriage/proximity-hearts accessors for the RPG disposition layer.
    // ---------------------------------------------------------------------------------------------

    /**
     * True if this player is married (to a villager or player) per MCA player save data (which
     * implements MCA's relationship interface directly, so its own {@code isMarried()} already covers
     * both the married-to-villager and married-to-player states). Safe default: {@code false}.
     */
    public static boolean isPlayerMarried(ServerPlayer player) {
        try {
            return McaHandles.isMarried(McaHandles.playerSave(player));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isPlayerMarried failed; defaulting false", t);
            return false;
        }
    }

    /**
     * Highest MCA hearts value between the player and any loaded MCA villager (adult or child — hearts
     * are hearts) within {@code radius} blocks. One bounded entity scan — callers must throttle. Safe
     * default: {@code empty} (none loaded / MCA absent / any failure).
     */
    public static OptionalInt maxHeartsWithin(ServerPlayer player, double radius) {
        try {
            OptionalInt max = OptionalInt.empty();
            for (Entity villager : villagersNear(player, radius)) {
                int hearts = getHearts(player, villager);
                if (max.isEmpty() || hearts > max.getAsInt()) {
                    max = OptionalInt.of(hearts);
                }
            }
            return max;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA maxHeartsWithin failed; defaulting empty", t);
            return OptionalInt.empty();
        }
    }

    /**
     * The loaded MCA villager within {@code radius} blocks of the player with the highest relationship
     * hearts (ties keep the first one scanned) — the entity-returning sibling of {@link #maxHeartsWithin},
     * added for the FTBQ {@code mcaquests:hearts} task's {@code spouse_only} mode (spec §15.9), which needs
     * to test {@link #isPlayerSpouse} against the same "best hearts" villager rather than duplicating the
     * MCA scan. One bounded entity scan — callers must throttle. Safe default: {@code empty}.
     */
    public static Optional<Entity> bestHeartsVillagerWithin(ServerPlayer player, double radius) {
        try {
            Entity best = null;
            int bestHearts = Integer.MIN_VALUE;
            for (Entity villager : villagersNear(player, radius)) {
                int hearts = getHearts(player, villager);
                if (best == null || hearts > bestHearts) {
                    best = villager;
                    bestHearts = hearts;
                }
            }
            return Optional.ofNullable(best);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA bestHeartsVillagerWithin failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * The loaded MCA villager within {@code radius} blocks of the player that is genuinely nearest by
     * (squared) distance — the distance-ranked sibling of {@link #bestHeartsVillagerWithin}, added for
     * the banked {@code mcaquests:hearts} claim's {@code NEAREST_VILLAGER} target (spec §16.2, task
     * M3.1), which the spec words as "nearest", not "best hearts". One bounded entity scan — callers
     * must throttle. Safe default: {@code empty}.
     */
    public static Optional<Entity> nearestVillagerWithin(ServerPlayer player, double radius) {
        try {
            Entity nearest = null;
            double nearestDistSqr = Double.MAX_VALUE;
            for (Entity villager : villagersNear(player, radius)) {
                double distSqr = villager.distanceToSqr(player);
                if (nearest == null || distSqr < nearestDistSqr) {
                    nearest = villager;
                    nearestDistSqr = distSqr;
                }
            }
            return Optional.ofNullable(nearest);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA nearestVillagerWithin failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * The nearest loaded <em>adult</em> MCA villager to the player within {@code radius} blocks — the
     * adult-filtered sibling of {@link #nearestVillagerWithin}, added for the FTBQ
     * {@code mcaquests:hearts} reward's {@code NEAREST_VILLAGER} target (spec §16.2 task M3.2: "nearest
     * loaded adult MCA villager"), and reused by {@code ProjectRewardDistributor}'s banked
     * {@code NEAREST_VILLAGER} delivery path so claim-now and deliver-later agree on what "nearest"
     * means. Filters inside the scan rather than delegating to {@link #nearestVillagerWithin} and
     * rejecting afterward, so a nearer child never shadows a slightly farther adult. One bounded
     * entity scan — callers must throttle. Safe default: {@code empty}.
     */
    public static Optional<Entity> nearestAdultVillagerWithin(ServerPlayer player, double radius) {
        try {
            Entity nearest = null;
            double nearestDistSqr = Double.MAX_VALUE;
            for (Entity villager : villagersNear(player, radius)) {
                if (!isAdult(villager)) {
                    continue;
                }
                double distSqr = villager.distanceToSqr(player);
                if (nearest == null || distSqr < nearestDistSqr) {
                    nearest = villager;
                    nearestDistSqr = distSqr;
                }
            }
            return Optional.ofNullable(nearest);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA nearestAdultVillagerWithin failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** Shared bounded scan behind the four proximity accessors above. */
    private static List<Entity> villagersNear(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return McaHandles.villagersWithin(player.level(), box);
    }

    /** Wraps an MCA village's id, mapping the "no village" sentinel to {@code empty}. */
    private static OptionalInt villageIdOf(Object village) {
        if (village == null) {
            return OptionalInt.empty();
        }
        int id = McaHandles.villageId(village);
        return id == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(id);
    }
}
