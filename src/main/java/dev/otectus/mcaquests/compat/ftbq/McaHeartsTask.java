package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@code mcaquests:hearts} (spec §15.9) — has the player earned {@code hearts} relationship points with
 * an MCA villager? {@code spouse_only} narrows the check to the player's spouse specifically, using the
 * sanctioned simpler check: among loaded villagers within the config scan radius, is the
 * <em>best-hearts</em> one the player's spouse and at/above the threshold (rather than separately
 * locating the spouse entity — one bounded scan either way, and a player is realistically closest with
 * their own spouse).
 *
 * <p><b>Performance contract</b> (spec §15.9): the MCA scan behind this check only ever runs from this
 * task's own poll — {@link McaBooleanTaskBase#autoSubmitOnPlayerTick()} (default every 5s) — because
 * {@code canSubmit} (and therefore {@link #check}) is only invoked from {@code AbstractBooleanTask}'s
 * {@code submitTask}, which the FTBQ scheduler only calls per-player at that cadence (or on explicit
 * click/login), never per-tick. It is also never invoked once the team has completed this task —
 * {@code AbstractBooleanTask.submitTask} short-circuits on {@code teamData.isCompleted(this)} before
 * reaching {@code canSubmit} at all, so a completed team's players are never scanned again. No MCA event
 * exists for hearts changes, so poll is authoritative and the qualifying villager must be loaded near the
 * player at a poll instant to be "witnessed" — natural in practice, since hearts are earned by proximity.
 */
public class McaHeartsTask extends McaBooleanTaskBase {

    private int hearts = 100;
    private boolean spouseOnly = false;

    public McaHeartsTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.HEARTS;
    }

    @Override
    protected boolean check(ServerPlayer player) {
        double radius = McaQuestsConfig.COMMON.ftbqHeartsScanRadius.get();
        if (spouseOnly) {
            Optional<Entity> best = McaCompat.bestHeartsVillagerWithin(player, radius);
            return best.filter(villager -> McaCompat.isPlayerSpouse(player, villager)
                    && McaCompat.getHearts(player, villager) >= hearts).isPresent();
        }
        OptionalInt max = McaCompat.maxHeartsWithin(player, radius);
        return max.isPresent() && max.getAsInt() >= hearts;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putInt("hearts", hearts);
        nbt.putBoolean("spouse_only", spouseOnly);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        hearts = nbt.getInt("hearts");
        spouseOnly = nbt.getBoolean("spouse_only");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(hearts);
        buffer.writeBoolean(spouseOnly);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        hearts = buffer.readVarInt();
        spouseOnly = buffer.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addInt("hearts", hearts, v -> hearts = v, 100, Integer.MIN_VALUE, Integer.MAX_VALUE)
                .setNameKey("ftbquests.task.mcaquests.hearts.hearts");
        config.addBool("spouse_only", spouseOnly, v -> spouseOnly = v, false)
                .setNameKey("ftbquests.task.mcaquests.hearts.spouse_only");
    }

    @Override
    public MutableComponent getAltTitle() {
        if (spouseOnly) {
            return Component.translatable("ftbquests.task.mcaquests.hearts.alt_title.spouse", hearts);
        }
        return Component.translatable("ftbquests.task.mcaquests.hearts.alt_title.any", hearts);
    }
}
