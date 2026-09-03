package dev.otectus.mcaquests.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * Fired by {@code SituationManager.resolveSuccess}/{@code resolveFailure}/{@code resolveCleared}, after
 * the situation's outcome has been applied and the instance closed (spec section 11.1). Server-side,
 * not cancellable.
 */
public class SituationResolvedEvent extends Event {

    /** How the situation closed. */
    public enum Resolution {
        SUCCESS,
        FAILURE,
        CLEARED
    }

    private final ResourceLocation definitionId;
    private final int villageId;
    private final Resolution resolution;
    private final Set<UUID> participants;
    @Nullable
    private final ServerPlayer resolvingPlayer;

    public SituationResolvedEvent(ResourceLocation definitionId, int villageId, Resolution resolution,
                                  Set<UUID> participants, @Nullable ServerPlayer resolvingPlayer) {
        this.definitionId = definitionId;
        this.villageId = villageId;
        this.resolution = resolution;
        this.participants = participants;
        this.resolvingPlayer = resolvingPlayer;
    }

    /** The {@code SituationDefinition}'s own id — the source definition, not the synthetic offer quest id. */
    public ResourceLocation getDefinitionId() {
        return definitionId;
    }

    public int getVillageId() {
        return villageId;
    }

    public Resolution getResolution() {
        return resolution;
    }

    /** Immutable; every player who accepted an offer copy of this situation. */
    public Set<UUID> getParticipants() {
        return participants;
    }

    /** Non-null only for {@link Resolution#SUCCESS} — the player whose completion resolved it. */
    @Nullable
    public ServerPlayer getResolvingPlayer() {
        return resolvingPlayer;
    }
}
