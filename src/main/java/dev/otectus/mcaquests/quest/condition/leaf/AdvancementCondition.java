package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Requires the player to have earned a given advancement (spec section 13). */
public record AdvancementCondition(ResourceLocation advancement) implements QuestCondition {

    public static final Codec<AdvancementCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("advancement").forGetter(AdvancementCondition::advancement)
    ).apply(instance, AdvancementCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.ADVANCEMENT;
    }

    @Override
    public boolean test(QuestContext context) {
        MinecraftServer server = context.player().getServer();
        if (server == null) {
            return false;
        }
        AdvancementHolder adv = server.getAdvancements().get(advancement);
        return adv != null && context.player().getAdvancements().getOrStartProgress(adv).isDone();
    }
}
