package dev.otectus.mcaquests.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Immutable snapshot of an MCA villager's identity, captured when a quest is accepted (spec
 * section 16). Quest state references villagers only through this snapshot + UUID, so quests survive
 * the villager unloading, moving, or dying.
 */
public record McaVillagerRef(UUID uuid, Component displayName, ResourceLocation professionId) {

    public static McaVillagerRef of(Entity villager) {
        return new McaVillagerRef(
                McaCompat.getVillagerUuid(villager),
                McaCompat.getVillagerDisplayName(villager),
                McaCompat.getProfessionId(villager).orElse(null));
    }
}
