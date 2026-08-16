package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.DisplayNames;
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
 * Complete a number of trades with a villager. Credited on Forge's {@code TradeWithVillagerEvent},
 * which fires for MCA villagers (they inherit vanilla {@code AbstractVillager.notifyTrade}). With no
 * {@code villager}/{@code profession} given, any MCA villager trade counts.
 */
public record TradeWithVillagerObjective(Optional<VillagerTarget> villager,
                                         Optional<ResourceLocation> profession, int count) implements QuestObjective {

    public static final Codec<TradeWithVillagerObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VillagerTarget.CODEC.lenientOptionalFieldOf("villager").forGetter(TradeWithVillagerObjective::villager),
            ResourceLocation.CODEC.lenientOptionalFieldOf("profession").forGetter(TradeWithVillagerObjective::profession),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(TradeWithVillagerObjective::count)
    ).apply(instance, TradeWithVillagerObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TRADE_WITH_VILLAGER;
    }

    @Override
    public Component describe() {
        Component who = villager.map(VillagerTarget::describe)
                .or(() -> profession.map(DisplayNames::name))
                .orElseGet(() -> Component.translatable("mcaquests.target.villager.someone"));
        return Component.translatable("mcaquests.objective.trade_with_villager", count, who);
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

    /** Credit one completed trade with {@code merchant} if it matches the configured filter. */
    public void onTrade(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                        LivingEntity merchant, ServerLevel level) {
        if (progress.count() >= count || !McaCompat.isMcaVillager(merchant)) {
            return;
        }
        if (villager.isPresent()) {
            if (!villager.get().matches(merchant, player, active, level)) {
                return;
            }
        } else if (profession.isPresent()
                && !McaCompat.getProfessionId(merchant).map(profession.get()::equals).orElse(false)) {
            return;
        }
        progress.add(1);
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        String prefix = "Quest '" + questId + "': objective[" + index + "]";
        if (villager.isPresent() && profession.isPresent()) {
            errors.add(prefix + " sets both 'villager' and 'profession'; use only one.");
        }
        villager.ifPresent(v -> v.validate(prefix + " villager", errors));
    }
}
