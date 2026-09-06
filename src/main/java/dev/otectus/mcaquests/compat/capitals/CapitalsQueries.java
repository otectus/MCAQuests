package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The three ways MCA: Quests finds the capital a piece of content is <em>about</em>.
 *
 * <p>Conditions, the {@code capital_role} target and the rewards all need the same answer from
 * different starting points — a giver entity, a frozen reward context, a player standing somewhere —
 * and getting the level wrong is the failure mode that would be hardest to see. Capitals filters its
 * village lookup by the level's dimension, so a capital in the Nether is invisible to a query made
 * against the overworld and simply reads as "no capital here". Every method below therefore uses the
 * subject's own level and never {@code server.overworld()}.
 */
public final class CapitalsQueries {

    /**
     * How far a player may stand from a village and still count as being in it. The radius the FTB
     * Quests rewards and the project scope resolver already use, so a player who is "in the village"
     * for one is in it for the other.
     */
    private static final int VILLAGE_RESOLUTION_RADIUS = 128;

    private CapitalsQueries() {
    }

    /**
     * The capital of the village a quest giver belongs to.
     *
     * <p>The giver's own level is the right dimension by construction: the villager is standing in the
     * village being asked about.
     */
    public static Optional<CapitalRef> giverCapital(@Nullable Entity giver) {
        if (giver == null || !(giver.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(giver);
        return villageId.isPresent()
                ? CapitalsCompat.bridge().capitalForVillage(level, villageId.getAsInt())
                : Optional.empty();
    }

    /**
     * The capital of the village a quest was accepted in, from the context a reward is granted with.
     *
     * <p>Uses the frozen dimension and village rather than where the player happens to be standing at
     * turn-in, so a knighthood granted for a quest taken in one capital cannot land in another.
     */
    public static Optional<CapitalRef> capitalForContext(@Nullable MinecraftServer server,
                                                         @Nullable QuestReward.RewardContext context) {
        if (server == null || context == null || context.villageId().isEmpty()) {
            return Optional.empty();
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, context.dimension()));
        return level == null
                ? Optional.empty()
                : CapitalsCompat.bridge().capitalForVillage(level, context.villageId().getAsInt());
    }

    /** The capital of the village the player is standing in, if they are standing in one. */
    public static Optional<CapitalRef> playerVillageCapital(@Nullable ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        OptionalInt villageId = McaCompat.findNearestVillageId(level, player.blockPosition(),
                VILLAGE_RESOLUTION_RADIUS);
        return villageId.isPresent()
                ? CapitalsCompat.bridge().capitalForVillage(level, villageId.getAsInt())
                : Optional.empty();
    }

    /**
     * What to call a capital in front of a player: the village's own name, which is the name the
     * player already knows it by and the one Capitals itself derives its display name from.
     */
    public static Optional<String> capitalName(@Nullable ServerLevel level, @Nullable CapitalRef capital) {
        if (level == null || capital == null) {
            return Optional.empty();
        }
        return McaCompat.villageName(level, capital.villageId());
    }
}
