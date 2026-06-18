package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

import javax.annotation.Nullable;

/**
 * Talk to (interact with) MCA villagers of a given profession (spec section 14). MCA-specific; the
 * profession is read via {@code McaCompat} in the event handler.
 */
public record TalkToProfessionObjective(ResourceLocation profession, int count) implements QuestObjective {

    public static final Codec<TalkToProfessionObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("profession").forGetter(TalkToProfessionObjective::profession),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(TalkToProfessionObjective::count)
    ).apply(instance, TalkToProfessionObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TALK_TO_PROFESSION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.talk_to_profession", count,
                Component.literal(profession.toString()));
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

    public boolean matches(@Nullable ResourceLocation talkedToProfession) {
        return profession.equals(talkedToProfession);
    }
}
