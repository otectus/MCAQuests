package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Holds the {@link PlayerQuestData} data attachment and its registration (spec section 16).
 *
 * <p>PORT: this replaces the 1.20.1 Forge capability ({@code QuestCapabilities} +
 * {@code PlayerQuestDataProvider} + {@code AttachCapabilitiesEvent}). Attachments need no attach
 * step ({@code getData} creates lazily) and {@code .copyOnDeath()} replaces the old
 * {@code PlayerEvent.Clone} copy — the old handler copied in <em>all</em> clone cases, and
 * attachments survive non-death clones automatically, so behaviour is identical across death,
 * dimension change, and end-return.
 */
public final class QuestAttachments {

    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, McaQuests.MOD_ID);

    public static final Supplier<AttachmentType<PlayerQuestData>> PLAYER_QUESTS =
            REGISTER.register("player_quests", () -> AttachmentType
                    .serializable(PlayerQuestData::new)
                    .copyOnDeath()
                    .build());

    /**
     * The stable id, {@code mcaquests:player_quests} — the attachment key, and also the key the
     * 1.20.1 Forge build stored the capability under ({@code ForgeCaps} in the player file), which
     * the {@link ForgeCapsMigration} world-upgrade shim still reads.
     */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "player_quests");

    private QuestAttachments() {
    }

    /**
     * Optional-shaped accessor kept from the capability era so call sites don't change. The value
     * is always present now ({@code getData} creates on read); callers that used absence to mean
     * "no data yet" branch on the data's own emptiness instead.
     */
    public static Optional<PlayerQuestData> get(Player player) {
        return Optional.of(player.getData(PLAYER_QUESTS));
    }
}
