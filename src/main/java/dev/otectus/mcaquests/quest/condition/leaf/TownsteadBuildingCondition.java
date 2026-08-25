package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadVillageBuilding;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * True when the giver's home village has enough registered buildings of a type (Townstead spec §5.1).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_building",
 *   "building_type": "dock",
 *   "minimum_level": 2,
 *   "count": 1
 * }
 * }</pre>
 *
 * <p><b>Registered buildings, never blocks.</b> A player who assembles a dock-shaped pile of planks has
 * not built a dock until MCA says so, and testing the registry rather than the world is what stops a
 * lookalike from satisfying a quest.
 *
 * <p>{@code building_type} matches a family as well as an exact id, so {@code dock} covers
 * {@code dock_l1} through {@code dock_l3} and {@code minimum_level} picks the tier.
 */
public record TownsteadBuildingCondition(String buildingType, int minimumLevel, int count,
                                         OptionalInt minimumSize) implements QuestCondition {

    public static final Codec<TownsteadBuildingCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("building_type").forGetter(TownsteadBuildingCondition::buildingType),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_level", 1)
                            .forGetter(TownsteadBuildingCondition::minimumLevel),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1)
                            .forGetter(TownsteadBuildingCondition::count),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_size")
                            .forGetter(condition -> condition.minimumSize.isPresent()
                                    ? Optional.of(condition.minimumSize.getAsInt())
                                    : Optional.<Integer>empty())
            ).apply(instance, (type, level, count, size) -> new TownsteadBuildingCondition(
                    type, level, count, size.map(OptionalInt::of).orElseGet(OptionalInt::empty))));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.TOWNSTEAD_BUILDING;
    }

    @Override
    public boolean test(QuestContext context) {
        OptionalInt village = McaCompat.getHomeVillageId(context.villager());
        if (village.isEmpty()) {
            return false;
        }
        int matched = 0;
        for (TownsteadVillageBuilding building
                : context.mca().townstead().buildingsIn(context.level(), village.getAsInt())) {
            if (!building.matches(buildingType) || building.level() < minimumLevel) {
                continue;
            }
            if (minimumSize.isPresent() && building.size() < minimumSize.getAsInt()) {
                continue;
            }
            if (++matched >= count) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.townstead_building", count, buildingType);
    }
}
