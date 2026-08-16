package dev.otectus.mcaquests.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;

/**
 * Fired by {@code ReputationService.award} when a {@code "v:<id>"} village-reputation identity crosses
 * into a new tier, after the tier high-water mark has been advanced (spec section 11.1). Server-side,
 * not cancellable.
 */
public class ReputationTierReachedEvent extends Event {

    @Nullable
    private final ServerPlayer player;
    private final int villageId;
    private final ResourceLocation ladderId;
    private final String tier;
    private final int tierIndex;

    public ReputationTierReachedEvent(@Nullable ServerPlayer player, int villageId, ResourceLocation ladderId,
                                      String tier, int tierIndex) {
        this.player = player;
        this.villageId = villageId;
        this.ladderId = ladderId;
        this.tier = tier;
        this.tierIndex = tierIndex;
    }

    /** Null for offline/system awards (e.g. a project reward moving reputation with no online player). */
    @Nullable
    public ServerPlayer getPlayer() {
        return player;
    }

    public int getVillageId() {
        return villageId;
    }

    public ResourceLocation getLadderId() {
        return ladderId;
    }

    /** The newly-reached tier's id. */
    public String getTier() {
        return tier;
    }

    /** The newly-reached tier's index on the ladder. */
    public int getTierIndex() {
        return tierIndex;
    }
}
