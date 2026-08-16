package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Keep a villager (the giver by default) alive for a duration. Polled once per second: while the
 * target is loaded and alive (optionally only while near the player) the elapsed-tick counter
 * accrues. If the target dies before the window elapses, {@code QuestProgressEvents} either fails the
 * quest ({@code fail_on_death}) or resets the timer. Progress is measured in ticks but displayed in
 * seconds.
 */
public record ProtectEntityObjective(VillagerTarget villager, int durationTicks,
                                     boolean requireNearPlayer, int nearRadius,
                                     boolean failOnDeath) implements QuestObjective, VillagerTargeted {

    public static final Codec<ProtectEntityObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VillagerTarget.CODEC.lenientOptionalFieldOf("villager", VillagerTarget.SELF).forGetter(ProtectEntityObjective::villager),
            Codec.intRange(20, Integer.MAX_VALUE).lenientOptionalFieldOf("duration_ticks", 2400)
                    .forGetter(ProtectEntityObjective::durationTicks),
            Codec.BOOL.lenientOptionalFieldOf("require_near_player", false).forGetter(ProtectEntityObjective::requireNearPlayer),
            Codec.intRange(1, 64).lenientOptionalFieldOf("near_radius", 16).forGetter(ProtectEntityObjective::nearRadius),
            Codec.BOOL.lenientOptionalFieldOf("fail_on_death", true).forGetter(ProtectEntityObjective::failOnDeath)
    ).apply(instance, ProtectEntityObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.PROTECT_ENTITY;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.protect_entity", villager.describe(), durationTicks / 20);
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return Component.translatable("mcaquests.objective.protect_entity",
                villager.describeResolved(player, active, level), durationTicks / 20);
    }

    @Override
    public Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        return progress.elapsedTicks() >= durationTicks ? Optional.empty() : villager.resolve(player, active, level);
    }

    @Override
    public int required() {
        return durationTicks / 20;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min((int) (progress.elapsedTicks() / 20), durationTicks / 20);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.elapsedTicks() >= durationTicks;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /** Per-second poll: accrue protection time while the target is alive (and optionally near the player). */
    public void poll(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (progress.elapsedTicks() >= durationTicks) {
            return;
        }
        Optional<LivingEntity> target = villager.resolve(player, active, level);
        if (target.isEmpty() || !target.get().isAlive()) {
            return; // unloaded/absent — pause, do not fail (death is handled on LivingDeathEvent)
        }
        if (requireNearPlayer && !ObjectiveSupport.withinRadius(target.get(), player, nearRadius)) {
            return;
        }
        progress.addElapsed(20);
    }

    /** True when {@code dead} is the protected target (used by the death handler). */
    public boolean matchesTarget(LivingEntity dead, ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return villager.matches(dead, player, active, level);
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        villager.validate("Quest '" + questId + "': objective[" + index + "] villager", errors);
    }
}
