package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * True when a villager has, or has not, learned a Townstead profession skill (Townstead spec §5.1).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_skill",
 *   "target": "giver",
 *   "skill": "townstead:crop_rotation",
 *   "has": true
 * }
 * }</pre>
 *
 * <p>An unresolvable target answers false whichever way {@code has} points, because not being able to
 * look is not evidence either way, and a quest should not be offered on the strength of it.
 */
public record TownsteadSkillCondition(TownsteadTarget target, ResourceLocation skill,
                                      boolean has) implements QuestCondition {

    public static final Codec<TownsteadSkillCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadSkillCondition::target),
                    ResourceLocation.CODEC.fieldOf("skill").forGetter(TownsteadSkillCondition::skill),
                    StrictCodecs.strictOptional(Codec.BOOL, "has", true)
                            .forGetter(TownsteadSkillCondition::has)
            ).apply(instance, TownsteadSkillCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.TOWNSTEAD_SKILL;
    }

    @Override
    public boolean test(QuestContext context) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable()) {
            return false;
        }
        Entity villager = TownsteadTargetResolver
                .resolveForOffer(target, context.player(), context.villager(), context.level())
                .orElse(null);
        if (villager == null) {
            return false;
        }
        return bridge.hasSkill(villager, skill) == has;
    }

    @Override
    public Component describe() {
        return Component.translatable(has
                ? "mcaquests.condition.townstead_skill.has"
                : "mcaquests.condition.townstead_skill.lacks", skill.toString());
    }
}
