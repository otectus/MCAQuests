package dev.otectus.mcaquests.quest.guidance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Where the player is being sent, and which of their quests is sending them.
 *
 * <p>The quest is carried because the marker is not always about the quest the player pinned. A pinned
 * quest can have nothing to point at — "deliver 24 wheat", with no authored source, is a real objective
 * with no place attached — and the first shipped version of this let that suppress the marker for
 * <em>every</em> quest the player held, including an escort standing right beside it with a perfectly
 * good destination. The player saw no marker at all and concluded the feature did not work.
 *
 * <p>So guidance falls through to the next quest that can answer, and says which one answered, so the
 * tracker can draw its direction line under the right row rather than under the pinned one. The pin
 * still means "prefer this quest"; it no longer means "and show nothing if it cannot help".
 *
 * <p>Identified by quest id <em>and</em> giver, like {@code QuestLogEntry}, because the same quest can
 * be active from two different villagers.
 */
public record ActiveGuidance(ResourceLocation questId, UUID villagerUuid, GuidanceTarget target) {

    public static void encode(RegistryFriendlyByteBuf buf, ActiveGuidance guidance) {
        buf.writeResourceLocation(guidance.questId);
        buf.writeUUID(guidance.villagerUuid);
        GuidanceTarget.encode(buf, guidance.target);
    }

    public static ActiveGuidance decode(RegistryFriendlyByteBuf buf) {
        return new ActiveGuidance(buf.readResourceLocation(), buf.readUUID(), GuidanceTarget.decode(buf));
    }

    /** Whether this guidance is about the quest {@code questId} from {@code villagerUuid}. */
    public boolean isAbout(ResourceLocation questId, UUID villagerUuid) {
        return this.questId.equals(questId) && this.villagerUuid.equals(villagerUuid);
    }
}
