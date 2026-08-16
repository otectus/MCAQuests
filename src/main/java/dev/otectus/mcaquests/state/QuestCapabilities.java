package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

import java.util.Optional;

/** Holds the {@link PlayerQuestData} capability token and registration (spec section 16). */
public final class QuestCapabilities {

    public static final Capability<PlayerQuestData> PLAYER_QUESTS =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    public static final ResourceLocation ID = new ResourceLocation(McaQuests.MOD_ID, "player_quests");

    private QuestCapabilities() {
    }

    /** Registered on the mod event bus. */
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerQuestData.class);
    }

    public static Optional<PlayerQuestData> get(Player player) {
        return player.getCapability(PLAYER_QUESTS).resolve();
    }
}
