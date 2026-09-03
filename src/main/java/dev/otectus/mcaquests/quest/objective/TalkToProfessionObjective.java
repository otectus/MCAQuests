package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.DisplayNames;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

import javax.annotation.Nullable;

/**
 * Talk to (interact with) MCA villagers of a given profession (spec section 14). MCA-specific; the
 * profession is read via {@code McaCompat} in the event handler.
 */
public record TalkToProfessionObjective(ResourceLocation profession, int count) implements QuestObjective {

    public static final MapCodec<TalkToProfessionObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("profession").forGetter(TalkToProfessionObjective::profession),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(TalkToProfessionObjective::count)
    ).apply(instance, TalkToProfessionObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TALK_TO_PROFESSION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.talk_to_profession", count,
                DisplayNames.name(profession));
    }

    /**
     * The nearest villager of the profession the player has not already spoken to.
     *
     * <p>"Speak with three of our farmers" is only obvious in a village the player already knows, and
     * the objective deliberately counts <em>distinct</em> villagers — so the useful marker is not any
     * farmer, it is one who does not count yet. {@code progress.hasTalkedTo} already remembers who
     * does, so this simply skips them and the marker steps to the next one as each is talked to.
     *
     * <p>Scans loaded entities around the player only. A villager who is not loaded cannot be talked
     * to either, so there is nothing to point at and nothing is pointed at.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        return level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(SEARCH_RADIUS),
                        e -> dev.otectus.mcaquests.compat.McaCompat.isMcaVillager(e)
                                && !progress.hasTalkedTo(e.getUUID())
                                && matches(dev.otectus.mcaquests.compat.McaCompat
                                        .getProfessionId(e).orElse(null)))
                .stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .map(villager -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofEntity(villager, dev.otectus.mcaquests.quest.guidance.GuidanceKind.VILLAGER,
                        dev.otectus.mcaquests.compat.McaCompat.getVillagerDisplayName(villager)));
    }

    /** Blocks around the player to look in. Beyond loaded range there is nobody to talk to anyway. */
    private static final double SEARCH_RADIUS = 64.0D;

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
     * Profession match under the configured {@code professionMatchingMode}, so a datapack asking for
     * {@code minecraft:cartographer} still matches an MCA villager whose profession id is namespaced
     * differently (the default {@code NORMALIZED} mode ignores the namespace).
     */
    public boolean matches(@Nullable ResourceLocation talkedToProfession) {
        return ProfessionMatcher.matches(profession, talkedToProfession);
    }
}
