package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.state.BankedReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.OptionalInt;

/**
 * {@code mcaquests:village_reputation} (spec §16.1) — grants village reputation through the same
 * {@link ReputationService#award} funnel every other reputation-moving path uses, so tier-ups and toasts
 * fire normally. {@code NEAREST} resolves via {@link McaCompat#findNearestVillageId} within 128 blocks
 * (matching {@code ProjectRewardDistributor}'s {@code VILLAGE_RESOLUTION_RADIUS}); {@code
 * HIGHEST_REPUTATION} takes the argmax over {@link ReputationService#allVillageReputations}, breaking
 * ties by nearest village center to the player. Either way, no resolvable village banks the claim
 * (§16.1: "never silently waste a claim") rather than dropping it.
 */
public class McaVillageReputationReward extends McaRewardBase {

    /** Matches {@code ProjectRewardDistributor.VILLAGE_RESOLUTION_RADIUS} (spec §16.1). */
    private static final int VILLAGE_RESOLUTION_RADIUS = 128;

    private static final NameMap<Target> TARGET_NAME_MAP = NameMap.of(Target.NEAREST, Target.values())
            .baseNameKey("ftbquests.reward.mcaquests.village_reputation.target")
            .create();

    private int amount = 10;
    private Target target = Target.NEAREST;

    public McaVillageReputationReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FtbqRewardTypes.VILLAGE_REPUTATION;
    }

    @Override
    protected void doClaim(ServerPlayer player, boolean notify) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        OptionalInt villageId = resolveVillage(level, server, player);
        if (villageId.isPresent()) {
            ReputationService.award(server, "v:" + villageId.getAsInt(), amount, player);
            return;
        }
        // Not found - bank it (§16.1: never silently waste a claim). Re-attempted on login/daily tick
        // by ProjectManager.deliverPending -> ProjectRewardDistributor.attemptBankedDelivery (task M3.1).
        ProjectSavedData.get(server).addBankedReward(player.getUUID(), BankedReward.reputation(amount));
        player.sendSystemMessage(Component.translatable("mcaquests.ftbq.reward.banked_reputation"));
    }

    private OptionalInt resolveVillage(ServerLevel level, MinecraftServer server, ServerPlayer player) {
        if (target == Target.HIGHEST_REPUTATION) {
            return highestReputationVillage(level, server, player);
        }
        return McaCompat.findNearestVillageId(level, player.blockPosition(), VILLAGE_RESOLUTION_RADIUS);
    }

    /** Argmax over recorded village reputations; ties broken by nearest village center to the player. */
    private OptionalInt highestReputationVillage(ServerLevel level, MinecraftServer server, ServerPlayer player) {
        Map<Integer, Integer> reputations = ReputationService.allVillageReputations(server);
        if (reputations.isEmpty()) {
            return OptionalInt.empty();
        }
        int max = Integer.MIN_VALUE;
        for (int rep : reputations.values()) {
            if (rep > max) {
                max = rep;
            }
        }
        int best = -1;
        double bestDistSqr = Double.MAX_VALUE;
        for (Map.Entry<Integer, Integer> entry : reputations.entrySet()) {
            if (entry.getValue() != max) {
                continue;
            }
            int candidate = entry.getKey();
            double distSqr = McaCompat.villageCenter(level, candidate)
                    .map(pos -> pos.distSqr(player.blockPosition()))
                    .orElse(Double.MAX_VALUE);
            if (best == -1 || distSqr < bestDistSqr) {
                best = candidate;
                bestDistSqr = distSqr;
            }
        }
        return best == -1 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putInt("amount", amount);
        nbt.putString("target", target.name());
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        amount = Math.max(-1000, nbt.getInt("amount"));
        target = parseTarget(nbt.getString("target"));
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(amount);
        TARGET_NAME_MAP.write(buffer, target);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        amount = buffer.readVarInt();
        target = TARGET_NAME_MAP.read(buffer);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addInt("amount", amount, v -> amount = v, 10, -1000, Integer.MAX_VALUE)
                .setNameKey("ftbquests.reward.mcaquests.village_reputation.amount");
        config.addEnum("target", target, v -> target = v, TARGET_NAME_MAP)
                .setNameKey("ftbquests.reward.mcaquests.village_reputation.target");
    }

    private static Target parseTarget(String raw) {
        return parseEnum(Target.class, raw, Target.NEAREST);
    }

    public enum Target {
        NEAREST, HIGHEST_REPUTATION
    }
}
