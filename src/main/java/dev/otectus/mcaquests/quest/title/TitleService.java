package dev.otectus.mcaquests.quest.title;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.api.event.TitleGrantedEvent;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

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
        return grant(player, scope, title, giver, Optional.empty());
    }

    /**
     * As {@link #grant(ServerPlayer, TitleScope, ResourceLocation, Entity)}, falling back to
     * {@code frozen} when the giver entity is not loaded — the normal case for a quest completed in the
     * field, which used to drop the title silently.
     */
    public static boolean grant(ServerPlayer player, TitleScope scope, ResourceLocation title,
                                @Nullable Entity giver, Optional<QuestReputation.Community> frozen) {
        if (scope == TitleScope.GLOBAL) {
            return grantGlobal(player, title);
        }
        OptionalInt resolved = resolveVillage(player, giver);
        if (resolved.isPresent()) {
            return grantVillage(player, resolved.getAsInt(), title);
        }
        return frozen
                .map(community -> grantVillage(player, community.dimension(), community.villageId(), title))
                .orElse(false);
    }

    public static boolean grantGlobal(ServerPlayer player, ResourceLocation title) {
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        boolean granted = data.map(d -> d.titles().grantGlobal(title)).orElse(false);
        if (granted) {
            postGranted(player, title, TitleScope.GLOBAL, OptionalInt.empty());
        }
        return granted;
    }

    public static boolean grantVillage(ServerPlayer player, int villageId, ResourceLocation title) {
        // The village is keyed with the dimension the player is standing in — the only level a
        // Quests-resolved village id can refer to (ids are per-level in MCA).
        return grantVillage(player, player.level().dimension().location(), villageId, title);
    }

    /**
     * Grants in a named dimension rather than the one the player happens to be standing in, for a quest
     * whose village was frozen at accept time and completed somewhere else.
     */
    public static boolean grantVillage(ServerPlayer player, ResourceLocation dimension, int villageId,
                                       ResourceLocation title) {
        Optional<PlayerQuestData> data = QuestCapabilities.get(player);
        boolean granted = data.map(d -> d.titles().grantVillage(dimension, villageId, title)).orElse(false);
        if (granted) {
            postGranted(player, title, TitleScope.VILLAGE, OptionalInt.of(villageId));
        }
        return granted;
    }

    /**
     * The single funnel through which {@link TitleGrantedEvent} is posted (Risk R1): it sits at the
     * innermost mutation points ({@link #grantGlobal}/{@link #grantVillage}, immediately after
     * {@code PlayerTitles} reports the title as newly added), so every grant path — the
     * {@code grant_title} reward via {@link #grant}, the reputation tier-up grant, and the admin
     * {@code /mcaquests title} command — emits exactly once, and re-grants never post. {@link #grant}
     * itself does not post; it only delegates, so delegation cannot double-post.
     */
    private static void postGranted(ServerPlayer player, ResourceLocation title, TitleScope scope, OptionalInt villageId) {
        NeoForge.EVENT_BUS.post(new TitleGrantedEvent(player, title, scope, villageId));
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
