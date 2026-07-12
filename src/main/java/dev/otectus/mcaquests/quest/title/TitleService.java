package dev.otectus.mcaquests.quest.title;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.api.event.TitleGrantedEvent;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Centralises title grants (spec 0.7.0) so the {@code grant_title} reward and reputation tier-up paths
 * share one implementation. {@code VILLAGE}-scoped grants resolve the giver's village the same way
 * village reputation does; if no village resolves the grant is a no-op (consistent with the
 * {@code village_reputation} reward). Returns whether the title was newly granted.
 */
public final class TitleService {

    private TitleService() {
    }

    public static boolean grant(ServerPlayer player, TitleScope scope, ResourceLocation title, @Nullable Entity giver) {
        if (scope == TitleScope.GLOBAL) {
            boolean granted = grantGlobal(player, title);
            if (granted) {
                postGranted(player, title, TitleScope.GLOBAL, OptionalInt.empty());
            }
            return granted;
        }
        OptionalInt villageId = resolveVillage(player, giver);
        if (villageId.isEmpty()) {
            return false;
        }
        boolean granted = grantVillage(player, villageId.getAsInt(), title);
        if (granted) {
            postGranted(player, title, scope, villageId);
        }
        return granted;
    }

    /**
     * The single funnel through which {@link TitleGrantedEvent} is posted (Risk R1): only {@link #grant}
     * calls this, and only when {@code PlayerTitles} reports the title as newly added, so a re-grant (or
     * the admin {@code /mcaquests title} command, which calls {@link #grantGlobal}/{@link #grantVillage}
     * directly) never double-posts.
     */
    private static void postGranted(ServerPlayer player, ResourceLocation title, TitleScope scope, OptionalInt villageId) {
        MinecraftForge.EVENT_BUS.post(new TitleGrantedEvent(player, title, scope, villageId));
    }

    public static boolean grantGlobal(ServerPlayer player, ResourceLocation title) {
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        return data.map(d -> d.titles().grantGlobal(title)).orElse(false);
    }

    public static boolean grantVillage(ServerPlayer player, int villageId, ResourceLocation title) {
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        return data.map(d -> d.titles().grantVillage(villageId, title)).orElse(false);
    }

    private static OptionalInt resolveVillage(ServerPlayer player, @Nullable Entity giver) {
        if (giver == null || !(player.level() instanceof ServerLevel level)) {
            return OptionalInt.empty();
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(giver);
        if (villageId.isEmpty()) {
            villageId = McaCompat.findNearestVillageId(level, giver.blockPosition(),
                    McaQuestsConfig.COMMON.defaultScopeFallbackRadius.get());
        }
        return villageId;
    }
}
