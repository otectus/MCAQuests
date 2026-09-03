package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.state.BankedReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.quest.reward.HeartsReward;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@code mcaquests:hearts} (spec §16.2) — grants MCA relationship hearts via
 * {@link McaCompat#awardHearts}, so a village never loses a reward just because a resident is
 * unloaded: an absent villager's hearts are ledgered and paid when it next loads.
 *
 * <p><b>Clamp finding:</b> the configured {@code heartsRewardMultiplier}/min/max clamp
 * ({@code McaQuestsConfig}) is applied in {@link HeartsReward#effectiveAmount()} — the <em>native</em>
 * quest reward — not in {@link McaCompat#addHearts}, which is a bare additive call with no scaling of its
 * own. This reward is a sibling of {@code HeartsReward}, not a caller of it, so it must apply the same
 * clamp itself rather than assume {@code addHearts} already does: {@link #clampedAmount()} delegates to
 * {@code new HeartsReward(amount).effectiveAmount()} rather than re-deriving the formula, so the two
 * reward paths can never drift apart.
 *
 * <p><b>Adult filter:</b> {@code NEAREST_VILLAGER} resolves through
 * {@link McaCompat#nearestAdultVillagerWithin} (added alongside this task) rather than
 * {@link McaCompat#nearestVillagerWithin} — spec §16.2 says "nearest loaded <em>adult</em> MCA villager",
 * and filtering inside the scan (rather than scanning nearest-of-any-age and rejecting children after the
 * fact) means a nearer child never shadows a slightly farther adult. {@code ProjectRewardDistributor}'s
 * banked {@code NEAREST_VILLAGER} delivery path (task M3.1) is updated to the same helper so claim-now and
 * deliver-later semantics agree on "nearest" meaning "nearest adult".
 */
public class McaHeartsReward extends McaRewardBase {

    /** Matches {@code ProjectRewardDistributor.VILLAGER_RESOLUTION_RADIUS} (spec §16.2). */
    private static final double VILLAGER_RESOLUTION_RADIUS = 16;
    /** Matches {@code ProjectRewardDistributor.VILLAGE_RESOLUTION_RADIUS} (spec §16.1/§16.2 share it). */
    private static final int VILLAGE_RESOLUTION_RADIUS = 128;

    private static final NameMap<Target> TARGET_NAME_MAP = NameMap.of(Target.NEAREST_VILLAGER, Target.values())
            .baseNameKey("ftbquests.reward.mcaquests.hearts.target")
            .create();

    private int amount = 10;
    private Target target = Target.NEAREST_VILLAGER;

    public McaHeartsReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FtbqRewardTypes.HEARTS;
    }

    @Override
    protected void doClaim(ServerPlayer player, boolean notify) {
        // The `default` arm also covers "no target" (an absent/corrupt enum parses back to
        // NEAREST_VILLAGER, per §16.2's fallback) so every code path either delivers or banks.
        switch (target) {
            case SPOUSE -> claimSpouse(player);
            case VILLAGE_RESIDENTS -> claimVillageResidents(player);
            default -> claimNearestVillager(player);
        }
    }

    private void claimNearestVillager(ServerPlayer player) {
        Optional<Entity> villager = McaCompat.nearestAdultVillagerWithin(player, VILLAGER_RESOLUTION_RADIUS);
        if (villager.isEmpty()) {
            bank(player, Target.NEAREST_VILLAGER);
            return;
        }
        McaCompat.addHearts(player, villager.get(), clampedAmount());
    }

    /**
     * Reuses the "best hearts nearby, then confirm it's the spouse" idiom the FTBQ {@code hearts}
     * <em>task</em>'s {@code spouse_only} mode and the M3.1 banked-delivery path both already use.
     */
    private void claimSpouse(ServerPlayer player) {
        Optional<Entity> candidate = McaCompat.bestHeartsVillagerWithin(player, VILLAGER_RESOLUTION_RADIUS);
        if (candidate.isEmpty() || !McaCompat.isPlayerSpouse(player, candidate.get())) {
            bank(player, Target.SPOUSE);
            return;
        }
        McaCompat.addHearts(player, candidate.get(), clampedAmount());
    }

    private void claimVillageResidents(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            bank(player, Target.VILLAGE_RESIDENTS);
            return;
        }
        OptionalInt villageId = McaCompat.findNearestVillageId(level, player.blockPosition(), VILLAGE_RESOLUTION_RADIUS);
        if (villageId.isEmpty()) {
            bank(player, Target.VILLAGE_RESIDENTS);
            return;
        }
        int id = villageId.getAsInt();
        int clamped = clampedAmount();
        for (UUID residentUuid : McaCompat.villageResidentUuids(level, id)) {
            McaCompat.awardHearts(level, residentUuid, player, clamped);
        }
    }

    /** {@code amount} run through the same multiplier/clamp {@link HeartsReward#grant} applies. */
    private int clampedAmount() {
        return new HeartsReward(amount).effectiveAmount();
    }

    /**
     * Not found - bank it (mirrors §16.1's "never silently waste a claim"). Banks the raw configured
     * {@code amount}, not the clamped one: the multiplier/clamp is a live config read, so re-deriving it
     * at delivery time (as {@code ProjectRewardDistributor.deliverBankedHearts} already does for every
     * other banked-hearts path) keeps a config change between claim and delivery applied consistently,
     * rather than baking in a stale clamp from claim time.
     */
    private void bank(ServerPlayer player, Target unresolvedTarget) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ProjectSavedData.get(server).addBankedReward(player.getUUID(),
                BankedReward.hearts(amount, unresolvedTarget.name()));
        player.sendSystemMessage(Component.translatable("mcaquests.ftbq.reward.banked_hearts"));
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putInt("amount", amount);
        nbt.putString("target", target.name());
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        amount = nbt.getInt("amount");
        target = parseTarget(nbt.getString("target"));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(amount);
        TARGET_NAME_MAP.write(buffer, target);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        amount = buffer.readVarInt();
        target = TARGET_NAME_MAP.read(buffer);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addInt("amount", amount, v -> amount = v, 10, Integer.MIN_VALUE, Integer.MAX_VALUE)
                .setNameKey("ftbquests.reward.mcaquests.hearts.amount");
        config.addEnum("target", target, v -> target = v, TARGET_NAME_MAP)
                .setNameKey("ftbquests.reward.mcaquests.hearts.target");
    }

    private static Target parseTarget(String raw) {
        return parseEnum(Target.class, raw, Target.NEAREST_VILLAGER);
    }

    public enum Target {
        NEAREST_VILLAGER, SPOUSE, VILLAGE_RESIDENTS
    }
}
