package dev.otectus.mcaquests.compat.reputation;

import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.state.VillageStanding;
import dev.otectus.mcareputation.api.ReputationMirror;
import dev.otectus.mcareputation.community.CommunityKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Keeps Quests' own {@link VillageStanding} in step with MCA: Reputation's canonical state
 * (spec §32.3).
 *
 * <h2>Why mirror at all</h2>
 *
 * <p>Because a player may remove MCA: Reputation later. Without a mirror, Quests would fall back to a
 * store that has been frozen since the day Reputation was installed, and every player's standing would
 * appear to reset to whatever it was months ago. With one, removing Reputation leaves Quests reading
 * the last canonical figures — the same numbers the player saw yesterday (§32.6).
 *
 * <p>The mirror is <b>bookkeeping only</b>. It is called after a canonical commit has already
 * succeeded, so it cannot influence or veto the outcome; it fires no events and sends no messages,
 * because the canonical commit already did both; and it never calls back into Reputation, which would
 * recurse. Score is written with {@code setScore} rather than a delta, so a mirror that misses one
 * update self-corrects on the next one instead of drifting.
 */
public final class QuestsReputationMirror implements ReputationMirror {

    private final MinecraftServer server;

    public QuestsReputationMirror(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String mirrorName() {
        return "mcaquests:fallback-store";
    }

    @Override
    public void mirrorScore(UUID player, CommunityKey community, int score, ResourceLocation ladder,
                            String highWaterTier) {
        ProjectSavedData data = ProjectSavedData.get(server);
        VillageStanding standing = data.standing();
        standing.setScore(player, community.dimension(), community.villageId(), score);
        if (highWaterTier != null && !highWaterTier.isBlank()) {
            standing.setTierHighWater(player, ladder, community.dimension(), community.villageId(),
                    highWaterTier);
        }
        data.standingChanged();
    }

    @Override
    public void mirrorVillageTitle(UUID player, CommunityKey community, ResourceLocation title) {
        ProjectSavedData data = ProjectSavedData.get(server);
        if (data.standing().grantVillageTitle(player, community.dimension(), community.villageId(), title)) {
            data.standingChanged();
        }
    }

    @Override
    public void mirrorGlobalTitle(UUID player, ResourceLocation title) {
        // Global titles live in the player's own capability data, which is only reachable while they
        // are online. An offline grant is not lost: the canonical store holds it, and the Journal sync
        // on the player's next login re-asserts it through the bridge.
        var online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            dev.otectus.mcaquests.quest.title.TitleService.grantGlobal(online, title);
        }
    }
}
