package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.title.TitleScope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.OptionalInt;

/**
 * Fired by {@code TitleService} (from {@code grantGlobal}/{@code grantVillage}, and therefore every
 * grant path including {@code grant}, the reputation tier-up, and the admin title command) after
 * {@code PlayerTitles} reports the title as newly granted — never on re-grants. Spec section 11.1;
 * server-side, not cancellable.
 */
public class TitleGrantedEvent extends Event {

    private final ServerPlayer player;
    private final ResourceLocation titleId;
    private final TitleScope scope;
    private final OptionalInt villageId;

    public TitleGrantedEvent(ServerPlayer player, ResourceLocation titleId, TitleScope scope, OptionalInt villageId) {
        this.player = player;
        this.titleId = titleId;
        this.scope = scope;
        this.villageId = villageId;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getTitleId() {
        return titleId;
    }

    public TitleScope getScope() {
        return scope;
    }

    /** Present only for {@link TitleScope#VILLAGE} grants. */
    public OptionalInt getVillageId() {
        return villageId;
    }
}
