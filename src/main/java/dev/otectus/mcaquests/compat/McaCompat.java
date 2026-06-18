package dev.otectus.mcaquests.compat;

import forge.net.mca.entity.VillagerEntityMCA;
import forge.net.mca.entity.VillagerLike;
import forge.net.mca.entity.ai.Memories;
import forge.net.mca.entity.ai.relationship.AgeState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
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

    /** Basic guard for menu/turn-in actions: a living, nearby MCA villager in the player's level. */
    public static boolean canPlayerInteract(ServerPlayer player, Entity villager) {
        return isMcaVillager(villager)
                && villager.isAlive()
                && villager.level() == player.level()
                && villager.distanceToSqr(player) <= 12.0D * 12.0D;
    }
}
