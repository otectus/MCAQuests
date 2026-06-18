package dev.otectus.mcaquests.quest.condition;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.state.PlayerQuestData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.Random;

/**
 * Everything a {@link QuestCondition} needs to evaluate (spec section 13): the player, the giver
 * villager, the player's quest data, and the quest being considered (for stable per-offer rolls).
 */
public record QuestContext(ServerPlayer player, Entity villager, PlayerQuestData data, ResourceLocation questId) {

    public ServerLevel level() {
        return (ServerLevel) player.level();
    }

    public long dayTime() {
        return level().getDayTime();
    }

    public long worldDay() {
        return dayTime() / 24000L;
    }

    public int hearts() {
        return McaCompat.getHearts(player, villager);
    }

    public Optional<ResourceLocation> profession() {
        return McaCompat.getProfessionId(villager);
    }

    /** A per-(player, villager, day, quest, salt) seeded RNG so condition rolls are stable per offer. */
    public Random stableRandom(String salt) {
        long seed = player.getUUID().hashCode() * 31L
                + villager.getUUID().hashCode() * 17L
                + worldDay() * 1000003L
                + ((long) questId.hashCode() ^ salt.hashCode());
        return new Random(seed);
    }
}
