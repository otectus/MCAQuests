package dev.otectus.mcaquests.api.event;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * Base class for MCA: Quests lifecycle events, fired server-side on the Forge event bus
 * ({@code MinecraftForge.EVENT_BUS}) so other mods can react (spec section 28). These are
 * notifications — they expose immutable data + live entities, never the mod's internal mutable state.
 */
public abstract class QuestEvent extends Event {

    private final ServerPlayer player;
    @Nullable
    private final Entity villager;
    private final QuestDefinition definition;

    protected QuestEvent(ServerPlayer player, @Nullable Entity villager, QuestDefinition definition) {
        this.player = player;
        this.villager = villager;
        this.definition = definition;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** The quest giver / turn-in villager; may be null when none is involved (e.g. self-complete). */
    @Nullable
    public Entity getVillager() {
        return villager;
    }

    public ResourceLocation getQuestId() {
        return definition.id();
    }

    /** The immutable quest definition. */
    public QuestDefinition getDefinition() {
        return definition;
    }
}
