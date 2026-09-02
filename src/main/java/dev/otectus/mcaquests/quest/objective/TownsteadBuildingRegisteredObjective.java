package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadVillageBuilding;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.TownsteadNames;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Complete when the giver's village has the buildings asked for (Townstead spec §5.2).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_building_registered",
 *   "building_type": "dock",
 *   "minimum_level": 2,
 *   "count": 1,
 *   "require_new_or_upgraded": true
 * }
 * }</pre>
 *
 * <p><b>Registered buildings, never blocks.</b> A pile of planks in the shape of a dock is not a dock
 * until MCA has registered it, so a lookalike cannot satisfy this.
 *
 * <p>{@code require_new_or_upgraded} is what makes "build us a dock" mean building one. On acceptance
 * it records the buildings that already qualify, and only something that appears afterwards — or an
 * existing building raised to a higher tier — counts. Without it the objective asks merely that the
 * village <em>have</em> the building, which is the right shape for "make sure we still have a dock"
 * but would otherwise complete instantly in a village that already did.
 */
public record TownsteadBuildingRegisteredObjective(String buildingType, int minimumLevel, int count,
                                                   OptionalInt minimumSize,
                                                   boolean requireNewOrUpgraded)
        implements PollingObjective, TownsteadObjective {

    /** {@code progress.extra()} sub-tag: building id -> the tier it was at when the quest was accepted. */
    private static final String K_PRE_EXISTING = "townstead_buildings_at_accept";

    public static final Codec<TownsteadBuildingRegisteredObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("building_type")
                            .forGetter(TownsteadBuildingRegisteredObjective::buildingType),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_level", 1)
                            .forGetter(TownsteadBuildingRegisteredObjective::minimumLevel),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1)
                            .forGetter(TownsteadBuildingRegisteredObjective::count),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_size")
                            .forGetter((TownsteadBuildingRegisteredObjective o) -> o.minimumSize().isPresent()
                                    ? java.util.Optional.of(o.minimumSize().getAsInt())
                                    : java.util.Optional.<Integer>empty()),
                    StrictCodecs.strictOptional(Codec.BOOL, "require_new_or_upgraded", true)
                            .forGetter(TownsteadBuildingRegisteredObjective::requireNewOrUpgraded)
            ).apply(instance, (type, level, count, size, requireNew) ->
                    new TownsteadBuildingRegisteredObjective(type, level, count,
                            size.map(OptionalInt::of).orElseGet(OptionalInt::empty), requireNew)));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TOWNSTEAD_BUILDING_REGISTERED;
    }

    /**
     * Building enumeration is an MCA read, so this needs no Townstead capability of its own — but it is
     * still a {@link TownsteadObjective}, because the type ids it matches come from Townstead and an
     * uninstalled Townstead means those buildings can never exist. Claiming {@code READ_BUILDING}
     * states that honestly and suspends the quest rather than leaving it unwinnable.
     */
    @Override
    public Set<TownsteadCapability> requiredCapabilities() {
        return Set.of(TownsteadCapability.READ_BUILDING);
    }


    /**
     * The building the work is about, or the village that needs one.
     *
     * <p>Eleven bundled objectives ask for a dock, a shed or a smokehouse and, until now, pointed at
     * nothing: the type id is a Townstead concept and the mod had no way to turn it into a place. It
     * does — MCA registers every building with a centre, which is what
     * {@link #qualifyingIgnoringLevel} already reads to take its acceptance snapshot.
     *
     * <p>Both readings of this objective get a true answer. "Make sure we still have a dock" points at
     * the dock. "Build us a dock", in a village that has a tier-one one to raise, points at the
     * building to raise; in a village with none at all there is no dock to point at and the answer
     * falls through to the settlement, which is where the player has to build it anyway.
     *
     * <p>Nearest to the player rather than to the giver, because unlike the frozen
     * {@code townstead_building} anchor this is not a commitment — nothing is recorded, the objective
     * counts buildings rather than one chosen building, and the nearest one is the one worth walking
     * to.
     */
    @Override
    public Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress) || !townsteadReady()) {
            return Optional.empty();
        }
        /* A building is a place you arrive at, not a block you stand on. */
        final int arriveRadius = 12;
        return qualifyingIgnoringLevel(player, active, level).stream()
                .min(Comparator.comparingDouble(b -> b.center().distSqr(player.blockPosition())))
                .map(building -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofPos(
                        building.center(), level,
                        dev.otectus.mcaquests.quest.guidance.GuidanceKind.STRUCTURE,
                        TownsteadNames.building(buildingType), arriveRadius, false))
                .or(() -> TownsteadObjective.super.guidance(player, active, progress, level));
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(count, progress.count());
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= count;
    }

    @Override
    public void freezeBaseline(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                               ServerLevel level) {
        if (!requireNewOrUpgraded || progress.extra().contains(K_PRE_EXISTING)) {
            return;
        }
        CompoundTag existing = new CompoundTag();
        for (TownsteadVillageBuilding building : qualifyingIgnoringLevel(player, active, level)) {
            existing.putInt(String.valueOf(building.id()), building.level());
        }
        // Written even when empty: its presence is what records that the snapshot was taken, so a
        // village that had no docks at accept time is not re-snapshotted after one is built.
        progress.extra().put(K_PRE_EXISTING, existing);
    }

    @Override
    public boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress) {
        ServerLevel level = (ServerLevel) player.level();
        if (requireNewOrUpgraded && !progress.extra().contains(K_PRE_EXISTING)) {
            freezeBaseline(player, quest, progress, level);
        }
        CompoundTag existing = progress.extra().getCompound(K_PRE_EXISTING);

        int qualifying = 0;
        for (TownsteadVillageBuilding building : qualifyingIgnoringLevel(player, quest, level)) {
            if (building.level() < minimumLevel) {
                continue;
            }
            if (minimumSize.isPresent() && building.size() < minimumSize.getAsInt()) {
                continue;
            }
            if (requireNewOrUpgraded) {
                String key = String.valueOf(building.id());
                // New building, or one that has risen above the tier it was at when we started.
                if (existing.contains(key) && building.level() <= existing.getInt(key)) {
                    continue;
                }
            }
            qualifying++;
        }

        if (qualifying <= progress.count()) {
            return false;
        }
        progress.setCount(Math.min(count, qualifying));
        return true;
    }

    /** Every building of the wanted family in the giver's village, before tier and size filtering. */
    private List<TownsteadVillageBuilding> qualifyingIgnoringLevel(ServerPlayer player, ActiveQuest active,
                                                                   ServerLevel level) {
        Entity giver = level.getEntity(active.villagerUuid());
        if (giver == null) {
            return List.of();
        }
        OptionalInt village = McaCompat.getHomeVillageId(giver);
        if (village.isEmpty()) {
            return List.of();
        }
        return new TownsteadEvaluation().buildingsIn(level, village.getAsInt()).stream()
                .filter(building -> building.matches(buildingType))
                .toList();
    }

    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        if (requireNewOrUpgraded) {
            return false; // nothing is "new" until the snapshot exists
        }
        OptionalInt village = McaCompat.getHomeVillageId(context.villager());
        return village.isPresent() && context.mca().townstead()
                .countBuildings(context.level(), village.getAsInt(), buildingType, minimumLevel) >= count;
    }

    @Override
    public Component describe() {
        return Component.translatable(requireNewOrUpgraded
                        ? "mcaquests.objective.townstead_building_new"
                        : "mcaquests.objective.townstead_building_have",
                count, TownsteadNames.building(buildingType), minimumLevel);
    }
}
