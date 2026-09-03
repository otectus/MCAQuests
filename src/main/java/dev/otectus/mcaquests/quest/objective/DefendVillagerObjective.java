package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.EntityTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
 * {@code radius} of the resolved villager. If the villager is unloaded at the kill moment the kill still
 * counts for a short grace window, measured from where they were last seen — see {@link #onKill}.
 */
public record DefendVillagerObjective(VillagerTarget villager, EntityTarget threat,
                                      int radius, int count) implements QuestObjective, VillagerTargeted {

    public static final MapCodec<DefendVillagerObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            VillagerTarget.CODEC.optionalFieldOf("villager", VillagerTarget.SELF).forGetter(DefendVillagerObjective::villager),
            EntityTarget.MAP_CODEC.fieldOf("threat").forGetter(DefendVillagerObjective::threat),
            Codec.intRange(1, 64).optionalFieldOf("radius", 16).forGetter(DefendVillagerObjective::radius),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 5).forGetter(DefendVillagerObjective::count)
    ).apply(instance, DefendVillagerObjective::new));

    /**
     * How long a kill still counts after the defended villager stopped resolving, in ticks (10 seconds).
     *
     * <p>A villager standing at the edge of a village drifts in and out of loaded chunks while the player
     * fights a few blocks away, and every kill landing in a gap used to be dropped in silence. Short
     * enough that it cannot mean "somewhere else entirely": the player has not left the fight.
     */
    private static final long LAST_SEEN_GRACE_TICKS = 200L;

    private static final String KEY_SEEN_TICK = "defend_seen_tick";
    private static final String KEY_SEEN_POS = "defend_seen_pos";

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
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        return Component.translatable("mcaquests.objective.defend_villager", count, threat.describe(),
                ObjectiveSupport.describeLocked(villager, player, active, progress, level));
    }

    @Override
    public VillagerTarget targetSelector() {
        return villager;
    }

    @Override
    public Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        if (progress.count() >= count) {
            return Optional.empty();
        }
        Optional<LivingEntity> resolved = ObjectiveSupport.resolveLocked(villager, player, active, progress, level);
        // The highlight runs every tick a quest is tracked, which makes it the cheapest honest place to
        // remember where the villager was while they were still loaded.
        resolved.ifPresent(defended -> rememberSeen(progress, defended, level));
        return resolved;
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

    /**
     * Credit a kill if {@code dead} is a matching hostile within range of the defended villager.
     *
     * <p>"Within range of the villager" is asked of the villager as they are now, and failing that of
     * where they were last seen, within {@link #LAST_SEEN_GRACE_TICKS}. The second question exists
     * because the first one silently answers "no" whenever the villager's chunk is unloaded — a
     * render-distance flicker at a village edge, not anything the player did — and the kill was thrown
     * away with no feedback at all. Outside the window nothing is remembered and the old behaviour
     * stands: threats only matter near a villager who is actually there.
     */
    public void onKill(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                       LivingEntity dead, ServerLevel level) {
        if (progress.count() >= count || !ObjectiveSupport.isHostile(dead) || !threat.matches(dead)) {
            return;
        }
        Optional<LivingEntity> defended = ObjectiveSupport.resolveLocked(villager, player, active, progress, level);
        if (defended.isPresent()) {
            rememberSeen(progress, defended.get(), level);
            if (ObjectiveSupport.withinRadius(dead, defended.get(), radius)) {
                progress.add(1);
            }
            return;
        }
        if (nearLastSeen(progress, dead, level)) {
            progress.add(1);
        }
    }

    /** Records where and when the defended villager last resolved, for the grace window above. */
    private static void rememberSeen(ObjectiveProgress progress, LivingEntity defended, ServerLevel level) {
        progress.extra().putLong(KEY_SEEN_TICK, level.getGameTime());
        progress.extra().putLong(KEY_SEEN_POS, defended.blockPosition().asLong());
    }

    /** True when the kill is inside the objective's radius of a recent enough last-known position. */
    private boolean nearLastSeen(ObjectiveProgress progress, LivingEntity dead, ServerLevel level) {
        CompoundTag extra = progress.extra();
        if (!extra.contains(KEY_SEEN_TICK, Tag.TAG_LONG) || !extra.contains(KEY_SEEN_POS, Tag.TAG_LONG)) {
            return false;
        }
        long age = level.getGameTime() - extra.getLong(KEY_SEEN_TICK);
        if (age < 0L || age > LAST_SEEN_GRACE_TICKS) {
            return false;
        }
        return ObjectiveSupport.withinRadius(dead, BlockPos.of(extra.getLong(KEY_SEEN_POS)), radius);
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        villager.validate("Quest '" + questId + "': objective[" + index + "] villager", errors);
    }
}
