package dev.otectus.mcaquests.quest.title;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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
        return scope == TitleScope.GLOBAL
                ? grantGlobal(player, title)
                : resolveVillage(player, giver).stream().anyMatch(id -> grantVillage(player, id, title));
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
