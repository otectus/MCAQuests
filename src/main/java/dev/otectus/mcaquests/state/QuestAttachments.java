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
 * Holds the {@link PlayerQuestData} data attachment and its registration (spec section 15).
 *
 * <p>PORT: this replaces the 1.20.1 Forge capability, its provider class and its attach event
 * ({@link QuestCapabilities} is now a thin façade over this). Attachments need no attach step
 * — {@code getData} creates the value lazily on first read — and {@code copyOnDeath()} replaces the
 * old {@code PlayerEvent.Clone} handler, which existed solely to carry the capability across a
 * respawn. Non-death clones (dimension change, end return) keep attachments automatically, so the
 * behaviour is the same in every case the old handler covered, without the revive/invalidate dance.
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
     * 1.20.1 Forge build stored the capability under (in the player file's {@code ForgeCaps}), which
     * {@link ForgeCapsMigration} still reads on world upgrade.
     */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "player_quests");

    private QuestAttachments() {
    }

    /**
     * This player's quest state, creating it if this is the first read.
     *
     * <p>Optional-shaped to match the capability accessor every call site was written against, but the
     * value is now always present: {@code getData} creates the attachment lazily rather than answering
     * "not attached yet". Callers that read absence as "no quest data" get an empty
     * {@link PlayerQuestData} instead, which is the same answer in every branch this mod takes.
     */
    public static Optional<PlayerQuestData> get(Player player) {
        return Optional.of(player.getData(PLAYER_QUESTS));
    }
}
