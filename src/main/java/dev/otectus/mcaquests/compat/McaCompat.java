package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import forge.net.mca.entity.VillagerEntityMCA;
import forge.net.mca.entity.VillagerLike;
import forge.net.mca.entity.ai.Memories;
import forge.net.mca.entity.ai.MoveState;
import forge.net.mca.entity.ai.relationship.AgeState;
import forge.net.mca.entity.ai.relationship.EntityRelationship;
import forge.net.mca.server.world.data.FamilyTree;
import forge.net.mca.server.world.data.FamilyTreeNode;
import forge.net.mca.server.world.data.PlayerSaveData;
import forge.net.mca.server.world.data.Village;
import forge.net.mca.server.world.data.VillageManager;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The single point of contact with Minecraft Comes Alive: Reborn.
 *
 * <p><b>Why {@code forge.net.mca.*} and not {@code net.mca.*}?</b> MCA Reborn ships a Forgix-merged
 * "Universal" jar (Forge + Fabric + Quilt). Forgix relocates each loader's classes under a
 * loader-named root package, so the Forge classes are physically {@code forge.net.mca.*} in both
 * the production jar and our dev-remapped (deobf) jar. There is no runtime restoration to
 * {@code net.mca.*}, so referencing the {@code forge.} prefix is correct for dev <em>and</em>
 * production. (MCA's own source is {@code net.mca.*}; the prefix is added at merge time.)
 *
 * <p>"Favor" in the spec maps to MCA "hearts" ({@link Memories#getHearts()}), reached per-player via
 * {@code villager.getVillagerBrain().getMemoriesForPlayer(player)}. Keep <em>all</em> MCA imports in
 * this class so MCA API drift only ever requires edits here.
 */
public final class McaCompat {

    /** Squared interaction/proximity radius shared by the interaction guard and "nearby" checks. */
    private static final double INTERACT_RANGE_SQR = 12.0D * 12.0D;

    /**
     * MCA's placeholder name for a player family-tree node it auto-creates before the player has set a
     * character name (MCA {@code PlayerSaveData.getFamilyEntry()} falls back to this when the entity is
     * offline/unresolvable). Treated as "no name set" so the Minecraft-username fallback still engages.
     */
    private static final String MCA_UNNAMED_PLACEHOLDER = "Unnamed Adventurer";

    private McaCompat() {
    }

    /** True for an adult-or-child MCA human villager (not the zombie variant). */
    public static boolean isMcaVillager(Entity entity) {
        return entity instanceof VillagerEntityMCA;
    }

    public static Optional<VillagerEntityMCA> asMcaVillager(Entity entity) {
        return entity instanceof VillagerEntityMCA villager ? Optional.of(villager) : Optional.empty();
    }

    public static UUID getVillagerUuid(Entity entity) {
        return entity.getUUID();
    }

    public static Component getVillagerDisplayName(Entity entity) {
        return entity instanceof VillagerEntityMCA villager ? villager.getDisplayName() : entity.getDisplayName();
    }

    /** Normalises the villager's profession to a {@link ResourceLocation} (spec section 12). */
    public static Optional<ResourceLocation> getProfessionId(Entity entity) {
        if (entity instanceof VillagerLike<?> villager) {
            return Optional.ofNullable(villager.getProfessionId());
        }
        return Optional.empty();
    }

    /** The villager's localised profession display name (e.g. "Farmer"), as MCA shows it. */
    public static Component getProfessionName(Entity entity) {
        if (entity instanceof VillagerLike<?> villager) {
            return villager.getProfessionText();
        }
        return Component.empty();
    }

    public static boolean isAdult(Entity entity) {
        if (entity instanceof VillagerLike<?> villager) {
            return villager.getAgeState() == AgeState.ADULT;
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
        if (villager instanceof VillagerEntityMCA mca) {
            Memories memories = mca.getVillagerBrain().getMemoriesForPlayer(player);
            return memories == null ? 0 : memories.getHearts();
        }
        return 0;
    }

    /**
     * Adds relationship hearts with this villager via MCA's own {@code VillagerBrain.rewardHearts}
     * (the same path MCA's gifting uses). <b>Server side only</b> — call after reward delivery.
     */
    public static void addHearts(ServerPlayer player, Entity villager, int amount) {
        if (amount == 0) {
            return;
        }
        if (villager instanceof VillagerEntityMCA mca) {
            mca.getVillagerBrain().rewardHearts(player, amount);
        }
    }

    /**
     * Controls whether a quest-giver follows the player (MCA {@code MoveState.FOLLOW}). When
     * {@code follow} is false this only resets a villager that is <em>currently</em> following, so a
     * player's manual STAY/MOVE choice is left untouched. <b>Server side only.</b>
     */
    public static void setQuestGiverFollow(ServerPlayer player, Entity villager, boolean follow) {
        if (villager instanceof VillagerEntityMCA mca) {
            if (follow) {
                mca.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
            } else if (mca.getVillagerBrain().getMoveState() == MoveState.FOLLOW) {
                mca.getVillagerBrain().setMoveState(MoveState.MOVE, player);
            }
        }
    }

    /**
     * Drives the villager to walk toward {@code dest} on its own — the NPC <em>leads</em> (the inverse of
     * {@link #setQuestGiverFollow}). Forces {@code MoveState.MOVE} (only when not already MOVE, to avoid
     * refreshing the brain every tick), then writes the vanilla {@link MemoryModuleType#WALK_TARGET} memory
     * that the villager's always-on move-to-walk-target behavior consumes to pathfind there. Re-issue each
     * poll — other brain behaviors can clear the walk target out from under us. <b>Server side only.</b>
     * No-op and fail-safe on a non-MCA entity or any error.
     */
    public static void leadVillagerTo(ServerPlayer player, Entity villager, BlockPos dest, double speed) {
        if (!(villager instanceof VillagerEntityMCA mca)) {
            return;
        }
        try {
            if (mca.getVillagerBrain().getMoveState() != MoveState.MOVE) {
                mca.getVillagerBrain().setMoveState(MoveState.MOVE, player);
            }
            mca.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
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
        if (!(villager instanceof VillagerEntityMCA mca)) {
            return;
        }
        try {
            mca.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            mca.getNavigation().stop();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA stopVillagerLeading failed; ignoring", t);
        }
    }

    /**
     * Pins a villager to its exact spot and makes it immune to all damage — used to "stage" an escortee so
     * it waits safely until the player reaches it. Uses vanilla {@code setNoAi} (a full AI/movement freeze,
     * not overridden by MCA) plus {@code setInvulnerable}; {@code VillagerEntityMCA#hurt} is {@code final}
     * and delegates to {@code super.hurt}, so the invulnerable flag is honored. Re-assert each tick while
     * holding (both flags persist to entity NBT, so a still-active quest self-heals on reload). Always undo
     * with {@link #releaseVillagerHold}. <b>Server side only.</b> Safe on a non-MCA entity or any error.
     */
    public static void holdVillagerInPlace(Entity villager) {
        if (!(villager instanceof VillagerEntityMCA mca)) {
            return;
        }
        try {
            mca.setNoAi(true);
            mca.setInvulnerable(true);
            mca.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            mca.getNavigation().stop();
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
        if (!(villager instanceof VillagerEntityMCA mca)) {
            return;
        }
        try {
            mca.setInvulnerable(false);
            mca.setNoAi(false);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA releaseVillagerHold failed; ignoring", t);
        }
    }

    /** Basic guard for menu/turn-in actions: a living, nearby MCA villager in the player's level. */
    public static boolean canPlayerInteract(ServerPlayer player, Entity villager) {
        return isMcaVillager(villager)
                && villager.isAlive()
                && villager.level() == player.level()
                && villager.distanceToSqr(player) <= INTERACT_RANGE_SQR;
    }

    // ---------------------------------------------------------------------------------------------
    // v0.3.0 — MCA-aware condition data points (see docs/0.3.0-design.md §2).
    //
    // Every method below fails safe: on a non-MCA entity, null/absent MCA data, or any throwable it
    // returns its documented safe default and logs at DEBUG. None of them ever propagate an
    // exception, so a malformed or partially-loaded relationship/family graph can never crash the
    // server during an eligibility pass.
    // ---------------------------------------------------------------------------------------------

    /** True when the villager is married specifically to this player. Safe default: {@code false}. */
    public static boolean isPlayerSpouse(ServerPlayer player, Entity villager) {
        try {
            return EntityRelationship.of(villager)
                    .map(rel -> rel.isMarriedTo(player.getUUID()))
                    .orElse(false);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isPlayerSpouse failed; defaulting false", t);
            return false;
        }
    }

    /** The villager's MCA relationship state (lowercased enum name). Safe default: {@code empty}. */
    public static Optional<String> getRelationshipState(Entity villager) {
        try {
            return EntityRelationship.of(villager)
                    .map(rel -> rel.getRelationshipState().name().toLowerCase(Locale.ROOT));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getRelationshipState failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * True when the villager is the given {@code relation} <em>to the player</em> in MCA's family
     * tree. {@code relation} is one of {@code any}/{@code parent}/{@code child}/{@code sibling}/
     * {@code grandparent}. Safe default: {@code false}.
     *
     * <p>Direction note (verified against MCA bytecode): {@code node.isParent(uuid)} means
     * "{@code uuid} is one of this node's parents". So the giver being the player's <em>child</em>
     * means the player is one of the giver's parents — {@code node.isParent(player)}.
     */
    public static boolean isFamilyOfPlayer(ServerPlayer player, Entity villager, String relation) {
        try {
            Optional<EntityRelationship> opt = EntityRelationship.of(villager);
            if (opt.isEmpty()) {
                return false;
            }
            FamilyTreeNode node = opt.get().getFamilyEntry();
            if (node == null) {
                return false;
            }
            UUID p = player.getUUID();
            return switch (relation) {
                case "any" -> node.isRelative(p);
                case "child" -> node.isParent(p);                       // player is giver's parent
                case "parent" -> node.streamChildren().anyMatch(p::equals); // player is giver's child
                case "sibling" -> node.siblings().contains(p);
                case "grandparent" -> {                                 // player is giver's grandchild
                    FamilyTree tree = opt.get().getFamilyTree();
                    yield node.streamChildren()
                            .map(tree::getOrEmpty)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .flatMap(FamilyTreeNode::streamChildren)
                            .anyMatch(p::equals);
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
        if (villager instanceof VillagerLike<?> v) {
            try {
                AgeState age = v.getAgeState();
                return age == null ? Optional.empty() : Optional.of(age.name().toLowerCase(Locale.ROOT));
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getAgeStateName failed; defaulting empty", t);
            }
        }
        return Optional.empty();
    }

    /** The villager's MCA personality (lowercased enum name). Safe default: {@code empty}. */
    public static Optional<String> getPersonalityName(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return Optional.of(mca.getVillagerBrain().getPersonality().name().toLowerCase(Locale.ROOT));
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getPersonalityName failed; defaulting empty", t);
            }
        }
        return Optional.empty();
    }

    /** The villager's current MCA mood value. Safe default: {@code empty}. */
    public static OptionalInt getMoodValue(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return OptionalInt.of(mca.getVillagerBrain().getMoodValue());
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getMoodValue failed; defaulting empty", t);
            }
        }
        return OptionalInt.empty();
    }

    /** The villager's current MCA mood name (lowercased). Safe default: {@code empty}. */
    public static Optional<String> getMoodName(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return Optional.ofNullable(mca.getVillagerBrain().getMood().getName())
                        .map(name -> name.toLowerCase(Locale.ROOT));
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getMoodName failed; defaulting empty", t);
            }
        }
        return Optional.empty();
    }

    /** True when the villager belongs to a home village. Safe default: {@code false}. */
    public static boolean hasHomeVillage(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return mca.getResidency().getHomeVillage().isPresent();
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA hasHomeVillage failed; defaulting false", t);
            }
        }
        return false;
    }

    /** True when the villager has an assigned home position. Safe default: {@code false}. */
    public static boolean hasHome(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return mca.getResidency().getHome().isPresent();
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA hasHome failed; defaulting false", t);
            }
        }
        return false;
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
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return mca.getInfectionProgress();
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getInfectionProgress failed; defaulting 0", t);
            }
        }
        return 0f;
    }

    /**
     * True when the giver has at least one relative of {@code relation}
     * ({@code spouse}/{@code parent}/{@code child}/{@code sibling}) whose {@code status}
     * ({@code alive}/{@code nearby}/{@code missing}/{@code dead}/{@code same_village}) matches.
     * Touches the persistent {@link FamilyTree}, so it resolves relatives even when they are not
     * currently loaded. Safe default: {@code false}.
     */
    public static boolean relativesWithStatus(ServerLevel level, Entity giver, String relation, String status) {
        try {
            Optional<EntityRelationship> opt = EntityRelationship.of(giver);
            if (opt.isEmpty()) {
                return false;
            }
            FamilyTreeNode node = opt.get().getFamilyEntry();
            if (node == null) {
                return false;
            }
            FamilyTree tree = opt.get().getFamilyTree();
            List<UUID> relatives = switch (relation) {
                case "spouse" -> {
                    UUID partner = node.partner();
                    yield (partner == null || partner.equals(Util.NIL_UUID)) ? List.of() : List.of(partner);
                }
                case "parent" -> node.streamParents().toList();
                case "child" -> node.streamChildren().toList();
                case "sibling" -> List.copyOf(node.siblings());
                default -> List.of();
            };
            for (UUID uuid : relatives) {
                if (matchesRelativeStatus(level, giver, tree, uuid, status)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA relativesWithStatus(relation={}, status={}) failed; defaulting false",
                    relation, status, t);
            return false;
        }
    }

    /**
     * True when {@code villager} has at least one missing relative of any tracked relation
     * (spouse/parent/child/sibling) — the village-scoped signal for the {@code missing_kin} situation
     * trigger (0.8.0). Safe default: {@code false}.
     */
    public static boolean hasMissingRelative(ServerLevel level, Entity villager) {
        return relativesWithStatus(level, villager, "spouse", "missing")
                || relativesWithStatus(level, villager, "parent", "missing")
                || relativesWithStatus(level, villager, "child", "missing")
                || relativesWithStatus(level, villager, "sibling", "missing");
    }

    private static boolean matchesRelativeStatus(ServerLevel level, Entity giver, FamilyTree tree,
                                                 UUID uuid, String status) {
        Optional<FamilyTreeNode> entry = tree.getOrEmpty(uuid);
        boolean deceased = entry.map(FamilyTreeNode::isDeceased).orElse(false);
        Entity entity = level.getEntity(uuid);
        boolean loaded = entity != null && entity.isAlive();
        return switch (status) {
            case "dead" -> deceased;
            case "alive" -> !deceased && (loaded || entry.isPresent());
            case "nearby" -> loaded && entity.distanceToSqr(giver) <= INTERACT_RANGE_SQR;
            case "missing" -> !deceased && entry.isPresent() && entity == null;
            case "same_village" -> giver instanceof VillagerEntityMCA mca
                    && mca.getResidency().getHomeVillage()
                    .map(village -> village.getResidentsUUIDs().anyMatch(uuid::equals))
                    .orElse(false);
            default -> false;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // v0.4.0 — Village/family identity for community projects (see docs/0.4.0-design.md §2/§4).
    //
    // MCA exposes a stable village id (Village#getId), a center anchor, a border test, the resident
    // set, and a queued-hearts path (Village#pushHearts(UUID,int)) for unloaded villagers. We surface
    // only primitives (OptionalInt / BlockPos / UUID / Set) so no forge.net.mca.* type escapes this
    // class. Every method fails safe to a documented default and never throws.
    // ---------------------------------------------------------------------------------------------

    /** The id of the villager's MCA home village, or empty when it has none / on any error. */
    public static OptionalInt getHomeVillageId(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return mca.getResidency().getHomeVillage().map(v -> OptionalInt.of(v.getId())).orElseGet(OptionalInt::empty);
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getHomeVillageId failed; defaulting empty", t);
            }
        }
        return OptionalInt.empty();
    }

    /** The villager's MCA home village display name. Safe default: {@code empty}. */
    public static Optional<String> getHomeVillageName(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return mca.getResidency().getHomeVillage().map(Village::getName);
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getHomeVillageName failed; defaulting empty", t);
            }
        }
        return Optional.empty();
    }

    /** The center (anchor) of the villager's MCA home village. Safe default: {@code empty}. */
    public static Optional<BlockPos> getHomeVillageCenter(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return mca.getResidency().getHomeVillage().map(v -> {
                    Vec3i c = v.getCenter();
                    return new BlockPos(c.getX(), c.getY(), c.getZ());
                });
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getHomeVillageCenter failed; defaulting empty", t);
            }
        }
        return Optional.empty();
    }

    /** The display name of a village resolved by id. Safe default: {@code empty}. */
    public static Optional<String> villageName(ServerLevel level, int villageId) {
        try {
            return VillageManager.get(level).getOrEmpty(villageId).map(Village::getName);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The center (anchor) of a village resolved by id. Safe default: {@code empty}. */
    public static Optional<BlockPos> villageCenter(ServerLevel level, int villageId) {
        try {
            return VillageManager.get(level).getOrEmpty(villageId).map(v -> {
                Vec3i c = v.getCenter();
                return new BlockPos(c.getX(), c.getY(), c.getZ());
            });
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageCenter failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /** The id of the nearest village to {@code pos} within {@code radius} blocks. Safe default: {@code empty}. */
    public static OptionalInt findNearestVillageId(ServerLevel level, BlockPos pos, int radius) {
        try {
            return VillageManager.get(level).findNearestVillage(pos, radius)
                    .map(v -> OptionalInt.of(v.getId())).orElseGet(OptionalInt::empty);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA findNearestVillageId failed; defaulting empty", t);
            return OptionalInt.empty();
        }
    }

    /** True when {@code pos} lies within the border of the village with {@code villageId}. Safe default: {@code false}. */
    public static boolean isWithinVillage(ServerLevel level, int villageId, BlockPos pos) {
        try {
            return VillageManager.get(level).getOrEmpty(villageId)
                    .map(v -> v.isWithinBorder(pos, 0)).orElse(false);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA isWithinVillage failed; defaulting false", t);
            return false;
        }
    }

    /** True when the village with {@code villageId} still exists. Safe default: {@code false}. */
    public static boolean villageExists(ServerLevel level, int villageId) {
        try {
            return VillageManager.get(level).getOrEmpty(villageId).isPresent();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageExists failed; defaulting false", t);
            return false;
        }
    }

    /** True when {@code uuid} is a resident of the village with {@code villageId}. Safe default: {@code false}. */
    public static boolean villageContains(ServerLevel level, int villageId, UUID uuid) {
        try {
            return VillageManager.get(level).getOrEmpty(villageId).map(v -> v.hasResident(uuid)).orElse(false);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageContains failed; defaulting false", t);
            return false;
        }
    }

    /**
     * The count of edible items currently banked in the village's MCA storage buffer — the famine
     * measure for the {@code low_food} situation trigger (0.8.0). Empty when the village does not
     * resolve or MCA storage is unavailable (the trigger then never fires). Safe-fail.
     */
    public static OptionalInt getVillageFoodCount(ServerLevel level, int villageId) {
        try {
            return VillageManager.get(level).getOrEmpty(villageId)
                    .map(v -> {
                        int food = 0;
                        for (ItemStack stack : v.storageBuffer) {
                            if (stack != null && stack.isEdible()) {
                                food += stack.getCount();
                            }
                        }
                        return OptionalInt.of(food);
                    })
                    .orElseGet(OptionalInt::empty);
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
            return VillageManager.get(level).getOrEmpty(villageId)
                    .map(v -> new ArrayList<Entity>(v.getResidents(level)))
                    .orElseGet(ArrayList::new);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA loadedVillageResidents failed; defaulting empty", t);
            return new ArrayList<>();
        }
    }

    /** The resident UUIDs of the village with {@code villageId}. Safe default: empty set. */
    public static Set<UUID> villageResidentUuids(ServerLevel level, int villageId) {
        try {
            return VillageManager.get(level).getOrEmpty(villageId)
                    .map(v -> {
                        Set<UUID> out = new HashSet<>();
                        v.getResidentsUUIDs().forEach(out::add);
                        return out;
                    })
                    .orElseGet(HashSet::new);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA villageResidentUuids failed; defaulting empty", t);
            return new HashSet<>();
        }
    }

    /**
     * Queues relationship hearts onto a village resident by UUID via MCA's own
     * {@code Village#pushHearts(UUID,int)} — applied to that villager the next time it loads. Use this
     * when a participating villager is offline/unloaded. <b>Server side only.</b>
     */
    public static void pushVillageHearts(ServerLevel level, int villageId, UUID villagerUuid, int amount) {
        if (amount == 0) {
            return;
        }
        try {
            VillageManager.get(level).getOrEmpty(villageId).ifPresent(v -> v.pushHearts(villagerUuid, amount));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA pushVillageHearts failed; ignoring", t);
        }
    }

    /**
     * A deterministic, stable identity for the villager's family <em>lineage</em>: the minimum UUID over
     * {@code {self} ∪ transitive ancestors} (bounded walk up the parent chain). Order-independent and
     * stable across relative deaths (deceased ancestors remain in the {@link FamilyTree}). This groups a
     * lineage, not a household. Safe default: {@code empty} (no family entry).
     */
    public static Optional<UUID> getFamilyRootId(Entity villager) {
        try {
            Optional<EntityRelationship> opt = EntityRelationship.of(villager);
            if (opt.isEmpty()) {
                return Optional.empty();
            }
            FamilyTreeNode node = opt.get().getFamilyEntry();
            if (node == null) {
                return Optional.empty();
            }
            FamilyTree tree = opt.get().getFamilyTree();
            UUID min = node.id();
            Set<UUID> visited = new HashSet<>();
            visited.add(node.id());
            List<UUID> frontier = new ArrayList<>(List.of(node.id()));
            for (int depth = 0; depth < 8 && !frontier.isEmpty(); depth++) {
                List<UUID> next = new ArrayList<>();
                for (UUID id : frontier) {
                    tree.getOrEmpty(id).ifPresent(n -> n.streamParents().forEach(parent -> {
                        if (visited.add(parent)) {
                            next.add(parent);
                        }
                    }));
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
    // MCA's Residency exposes the villager's assigned home (Optional<GlobalPos>) and workplace
    // (BlockPos), and the FamilyTree lets us pick a concrete relative of the giver. We surface only
    // primitives (BlockPos / UUID) so no forge.net.mca.* type escapes this class. Every method fails
    // safe to a documented default and never throws.
    // ---------------------------------------------------------------------------------------------

    /** The villager's assigned home/bed position (MCA {@code Residency#getHome}). Safe default: {@code empty}. */
    public static Optional<BlockPos> getHomePos(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                return mca.getResidency().getHome().map(GlobalPos::pos);
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getHomePos failed; defaulting empty", t);
            }
        }
        return Optional.empty();
    }

    /** The villager's workstation/job-site position (MCA {@code Residency#getWorkplace}). Safe default: {@code empty}. */
    public static Optional<BlockPos> getWorkstationPos(Entity villager) {
        if (villager instanceof VillagerEntityMCA mca) {
            try {
                BlockPos pos = mca.getResidency().getWorkplace();
                // MCA returns BlockPos.ZERO (and occasionally null) when no workplace is assigned.
                if (pos != null && !pos.equals(BlockPos.ZERO)) {
                    return Optional.of(pos);
                }
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("MCA getWorkstationPos failed; defaulting empty", t);
            }
        }
        return Optional.empty();
    }

    /**
     * Every relative of {@code giver} of the given {@code relation}
     * ({@code any}/{@code spouse}/{@code parent}/{@code child}/{@code sibling}), by UUID. Walks the
     * persistent {@link FamilyTree}, so it lists relatives even when they are unloaded. Self and the
     * MCA nil UUID are filtered out. Safe default: empty list.
     */
    public static List<UUID> giverRelativeUuids(ServerLevel level, Entity giver, String relation) {
        try {
            Optional<EntityRelationship> opt = EntityRelationship.of(giver);
            if (opt.isEmpty()) {
                return List.of();
            }
            FamilyTreeNode node = opt.get().getFamilyEntry();
            if (node == null) {
                return List.of();
            }
            List<UUID> relatives = new ArrayList<>();
            if (relation.equals("spouse") || relation.equals("any")) {
                UUID partner = node.partner();
                if (partner != null && !partner.equals(Util.NIL_UUID)) {
                    relatives.add(partner);
                }
            }
            if (relation.equals("parent") || relation.equals("any")) {
                node.streamParents().forEach(relatives::add);
            }
            if (relation.equals("child") || relation.equals("any")) {
                node.streamChildren().forEach(relatives::add);
            }
            if (relation.equals("sibling") || relation.equals("any")) {
                relatives.addAll(node.siblings());
            }
            UUID self = giver.getUUID();
            List<UUID> cleaned = new ArrayList<>();
            for (UUID uuid : relatives) {
                if (uuid != null && !uuid.equals(Util.NIL_UUID) && !uuid.equals(self) && !cleaned.contains(uuid)) {
                    cleaned.add(uuid);
                }
            }
            return cleaned;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA giverRelativeUuids(relation={}) failed; defaulting empty", relation, t);
            return List.of();
        }
    }

    /**
     * A concrete relative of {@code giver} of the given {@code relation}, preferring one that is
     * currently loaded so the caller can act on the entity. Safe default: {@code empty}.
     */
    public static Optional<UUID> findGiverRelative(ServerLevel level, Entity giver, String relation) {
        UUID firstKnown = null;
        for (UUID uuid : giverRelativeUuids(level, giver, relation)) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                return Optional.of(uuid); // prefer a loaded relative
            }
            if (firstKnown == null) {
                firstKnown = uuid;
            }
        }
        return Optional.ofNullable(firstKnown);
    }

    /**
     * The display name of a giver's relative by UUID, read from MCA's persistent {@link FamilyTree} so it
     * resolves <em>even when the relative is not currently loaded</em>. Lets a quest objective name the
     * villager the player must find. Safe default: {@code empty}.
     */
    public static Optional<String> getRelativeDisplayName(Entity giver, UUID relativeUuid) {
        try {
            return EntityRelationship.of(giver)
                    .map(EntityRelationship::getFamilyTree)
                    .flatMap(tree -> tree.getOrEmpty(relativeUuid))
                    .map(FamilyTreeNode::getName)
                    .filter(name -> name != null && !name.isBlank());
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("MCA getRelativeDisplayName failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    /**
     * The player's chosen MCA character name (set in MCA's character-creation screen), read from their
     * node in the persistent {@link FamilyTree} via {@link PlayerSaveData}. Empty when MCA is absent, the
     * player has not set a name, or it is blank. Safe default: {@code empty}.
     */
    public static Optional<String> getMcaPlayerName(ServerPlayer player) {
        try {
            return Optional.ofNullable(PlayerSaveData.get(player).getFamilyEntry().getName())
                    .filter(name -> !name.isBlank() && !name.equals(MCA_UNNAMED_PLACEHOLDER));
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
}
