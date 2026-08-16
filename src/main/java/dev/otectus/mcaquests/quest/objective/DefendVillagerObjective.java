package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.EntityTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Defeat a number of hostile threats near a villager (the giver by default). Credited on
 * {@code LivingDeathEvent} when the player kills a hostile matching {@code threat} within
 * {@code radius} of the resolved villager. If the villager is unloaded at the kill moment the kill is
 * not credited (threats only matter near a present villager).
 */
public record DefendVillagerObjective(VillagerTarget villager, EntityTarget threat,
                                      int radius, int count) implements QuestObjective, VillagerTargeted {

    public static final Codec<DefendVillagerObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VillagerTarget.CODEC.optionalFieldOf("villager", VillagerTarget.SELF).forGetter(DefendVillagerObjective::villager),
            EntityTarget.MAP_CODEC.fieldOf("threat").forGetter(DefendVillagerObjective::threat),
            Codec.intRange(1, 64).optionalFieldOf("radius", 16).forGetter(DefendVillagerObjective::radius),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 5).forGetter(DefendVillagerObjective::count)
    ).apply(instance, DefendVillagerObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.DEFEND_VILLAGER;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.defend_villager", count, threat.describe(), villager.describe());
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return Component.translatable("mcaquests.objective.defend_villager",
                count, threat.describe(), villager.describeResolved(player, active, level));
    }

    @Override
    public Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        return progress.count() >= count ? Optional.empty() : villager.resolve(player, active, level);
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), count);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /** Credit a kill if {@code dead} is a matching hostile within range of the resolved villager. */
    public void onKill(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                       LivingEntity dead, ServerLevel level) {
        if (progress.count() >= count || !ObjectiveSupport.isHostile(dead) || !threat.matches(dead)) {
            return;
        }
        Optional<LivingEntity> defended = villager.resolve(player, active, level);
        if (defended.isPresent() && ObjectiveSupport.withinRadius(dead, defended.get(), radius)) {
            progress.add(1);
        }
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        villager.validate("Quest '" + questId + "': objective[" + index + "] villager", errors);
    }
}
