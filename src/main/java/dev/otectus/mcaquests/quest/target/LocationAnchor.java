package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBuildings;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadVillageBuilding;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Resolves to a target {@link BlockPos} at runtime for the location-aware objective types (escort
 * destination, build-near site, breed/tame proximity). Resolution is by UUID / MCA lookup, so an
 * unloaded giver or despawned village resolves to {@code empty} (the objective pauses) rather than
 * failing.
 *
 * <pre>
 * { "anchor": "home_village" }                           // the giver's MCA home village
 * { "anchor": "nearest_village", "radius": 128 }         // nearest village to the giver, within radius
 * { "anchor": "nearest_other_village", "radius": 2048 }  // the next village along, never the giver's own
 * { "anchor": "giver_pos" }                               // the giver's current position
 * { "anchor": "villager", "villager": { "mode": "family", "relation": "spouse" } }
 * { "anchor": "workstation" }                             // the giver's job site
 * { "anchor": "bed" }                                     // the giver's home/bed position
 * { "anchor": "coords", "pos": [100, 64, -200] }
 * { "anchor": "townstead_building", "building_type": "dock", "minimum_level": 2 }
 * </pre>
 *
 * <h2>Live anchors and frozen anchors</h2>
 *
 * <p>Most anchors resolve live on every poll, which is what you want: a bed that moves with its owner
 * stays the right destination. The two added in 1.4.1 are {@link #freezes() frozen} instead, because
 * they are <em>choices</em> among several valid answers — which dock, which neighbouring village — and
 * a choice re-made every second is not a destination. See {@link FrozenLocation}.
 */
public record LocationAnchor(Type type, Optional<Integer> radius,
                             Optional<VillagerTarget> villager, Optional<BlockPos> pos,
                             Optional<String> buildingType, Optional<Integer> minimumLevel,
                             Selection selection) {

    public enum Type {
        HOME_VILLAGE, NEAREST_VILLAGE, GIVER_POS, VILLAGER, WORKSTATION, BED, COORDS,
        /** A registered Townstead/MCA building of a named family (spec §5.4). Frozen at acceptance. */
        TOWNSTEAD_BUILDING,
        /** The nearest registered MCA village that is <em>not</em> the giver's. Frozen at acceptance. */
        NEAREST_OTHER_VILLAGE
    }

    /** Which candidate a frozen anchor picks, when more than one qualifies. */
    public enum Selection {
        NEAREST_TO_GIVER, NEAREST_TO_PLAYER_AT_ACCEPT;

        private static final Codec<Selection> CODEC = Codec.STRING.flatXmap(
                s -> {
                    try {
                        return DataResult.success(Selection.valueOf(s.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown anchor selection '" + s
                                + "'; expected nearest_to_giver or nearest_to_player_at_accept");
                    }
                },
                s -> DataResult.success(s.name().toLowerCase(Locale.ROOT)));
    }

    private static final int DEFAULT_NEAREST_RADIUS = 128;
    /** The default reach of {@code nearest_other_village}; a route quest is meant to be a journey. */
    private static final int DEFAULT_OTHER_VILLAGE_RADIUS = 2048;

    private static final Codec<Type> TYPE_CODEC = Codec.STRING.flatXmap(
            s -> {
                try {
                    return DataResult.success(Type.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown location anchor: " + s);
                }
            },
            t -> DataResult.success(t.name().toLowerCase(Locale.ROOT)));

    public static final MapCodec<LocationAnchor> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TYPE_CODEC.fieldOf("anchor").forGetter(LocationAnchor::type),
            Codec.INT.optionalFieldOf("radius").forGetter(LocationAnchor::radius),
            VillagerTarget.CODEC.optionalFieldOf("villager").forGetter(LocationAnchor::villager),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(LocationAnchor::pos),
            Codec.STRING.optionalFieldOf("building_type").forGetter(LocationAnchor::buildingType),
            Codec.INT.optionalFieldOf("minimum_level").forGetter(LocationAnchor::minimumLevel),
            StrictCodecs.strictOptional(Selection.CODEC, "selection", Selection.NEAREST_TO_GIVER)
                    .forGetter(LocationAnchor::selection)
    ).apply(instance, LocationAnchor::new));

    public static final Codec<LocationAnchor> CODEC = MAP_CODEC.codec();

    /** The pre-1.4.1 shape, for the anchors that take none of the new fields. */
    public LocationAnchor(Type type, Optional<Integer> radius, Optional<VillagerTarget> villager,
                          Optional<BlockPos> pos) {
        this(type, radius, villager, pos, Optional.empty(), Optional.empty(),
                Selection.NEAREST_TO_GIVER);
    }

    /** The minimum registered tier a candidate building must have; {@code 1} by default. */
    public int effectiveMinimumLevel() {
        return Math.max(1, minimumLevel.orElse(1));
    }

    /**
     * True for an anchor whose answer must be decided once and kept. See the class javadoc for why
     * these two and not the others.
     */
    public boolean freezes() {
        return type == Type.TOWNSTEAD_BUILDING || type == Type.NEAREST_OTHER_VILLAGE;
    }

    /**
     * The identity two anchor specs share when they mean the same destination (spec §13.1).
     *
     * <p>Objectives in one quest bind their frozen location under this key, so the "place six lanterns
     * at the dock" and "place twelve chains at the dock" of a single stage are guaranteed to be talking
     * about the same dock. It deliberately omits everything that does not change which candidate is
     * chosen, so two spellings of the same requirement still collide.
     */
    public String fingerprint() {
        return switch (type) {
            case TOWNSTEAD_BUILDING -> "building/" + TownsteadBuildings.normalise(buildingType.orElse(""))
                    + '/' + effectiveMinimumLevel() + '/' + selection.name().toLowerCase(Locale.ROOT);
            case NEAREST_OTHER_VILLAGE -> "other_village/"
                    + radius.orElse(DEFAULT_OTHER_VILLAGE_RADIUS)
                    + '/' + selection.name().toLowerCase(Locale.ROOT);
            default -> type.name().toLowerCase(Locale.ROOT);
        };
    }

    public Optional<BlockPos> resolve(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return resolveTarget(player, active, level).map(Resolved::pos);
    }

    /**
     * A resolved anchor: a destination {@link BlockPos} plus, for the village anchors, the MCA village id
     * that resolved it. The id lets callers complete on the village <em>border</em> (anywhere inside the
     * village) rather than within a small radius of its center point.
     */
    public record Resolved(BlockPos pos, OptionalInt villageId) {
        static Resolved of(BlockPos pos) {
            return new Resolved(pos, OptionalInt.empty());
        }
    }

    /**
     * Resolves the anchor, additionally carrying the village id for the village anchors. The village
     * anchors resolve relative to the <em>giver/escortee</em> (not the player) so the target is stable
     * when frozen at accept and never snaps to a different village as the player wanders.
     *
     * <p>For a {@link #freezes() frozen} anchor this reads the binding recorded on the quest, resolving
     * and recording it on first use, so every later call gives the same answer.
     */
    public Optional<Resolved> resolveTarget(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        if (freezes()) {
            return frozen(player, active, level).map(FrozenLocation::resolved);
        }
        return resolveTarget(player, level.getEntity(active.villagerUuid()), level);
    }

    /**
     * The frozen destination for this anchor on this quest, choosing and recording one if the quest
     * does not have it yet. Empty when no candidate is currently resolvable — the objective pauses,
     * exactly as it does for an unloaded giver, and tries again later.
     */
    public Optional<FrozenLocation> frozen(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        FrozenLocation existing = active.frozenLocation(fingerprint());
        if (existing != null) {
            return Optional.of(existing);
        }
        Optional<FrozenLocation> chosen =
                choose(player, level.getEntity(active.villagerUuid()), level);
        chosen.ifPresent(location -> active.freezeLocation(fingerprint(), location));
        return chosen;
    }

    /**
     * Picks the candidate a frozen anchor should keep. Deterministic: nearest by squared distance,
     * ties broken by the registered id, so two players accepting the same quest in the same world get
     * the same building and a test can assert on it.
     */
    public Optional<FrozenLocation> choose(ServerPlayer player, @Nullable Entity giver, ServerLevel level) {
        return switch (type) {
            case TOWNSTEAD_BUILDING -> chooseBuilding(player, giver, level);
            case NEAREST_OTHER_VILLAGE -> chooseOtherVillage(player, giver, level);
            default -> resolveTarget(player, giver, level)
                    .map(resolved -> new FrozenLocation(resolved.pos(), level.dimension().location(),
                            resolved.villageId(), OptionalInt.empty(), Optional.empty(), OptionalInt.empty()));
        };
    }

    private Optional<FrozenLocation> chooseBuilding(ServerPlayer player, @Nullable Entity giver,
                                                    ServerLevel level) {
        if (giver == null || !TownsteadEvaluation.has(TownsteadCapability.READ_BUILDING)) {
            return Optional.empty();
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(giver);
        if (villageId.isEmpty()) {
            return Optional.empty();
        }
        BlockPos from = selection == Selection.NEAREST_TO_PLAYER_AT_ACCEPT
                ? player.blockPosition() : giver.blockPosition();
        String family = TownsteadBuildings.normalise(buildingType.orElse(""));
        int minimum = effectiveMinimumLevel();

        TownsteadVillageBuilding best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TownsteadVillageBuilding candidate
                : new TownsteadEvaluation().buildingsIn(level, villageId.getAsInt())) {
            if (!TownsteadBuildings.sameFamily(candidate.type(), family) || candidate.level() < minimum) {
                continue;
            }
            double distance = candidate.center().distSqr(from);
            // Strictly-nearer, then lower id: an equality tie must not depend on iteration order.
            if (distance < bestDistance
                    || (distance == bestDistance && best != null && candidate.id() < best.id())) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best == null ? Optional.empty()
                : Optional.of(FrozenLocation.building(best.center(), level.dimension().location(),
                        villageId.getAsInt(), best.id(), family, best.level()));
    }

    private Optional<FrozenLocation> chooseOtherVillage(ServerPlayer player, @Nullable Entity giver,
                                                        ServerLevel level) {
        BlockPos from = giver != null ? giver.blockPosition() : player.blockPosition();
        OptionalInt home = giver == null ? OptionalInt.empty() : McaCompat.getHomeVillageId(giver);
        OptionalInt chosen = McaCompat.findNearestVillageIdExcluding(level, from,
                radius.orElse(DEFAULT_OTHER_VILLAGE_RADIUS), home);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        return McaCompat.villageCenter(level, chosen.getAsInt())
                .map(center -> FrozenLocation.village(center, level.dimension().location(), chosen.getAsInt()));
    }

    /**
     * As {@link #resolveTarget(ServerPlayer, ActiveQuest, ServerLevel)}, but keyed on the giver entity
     * rather than on an accepted quest — which is all the {@link ActiveQuest} was ever used for here.
     *
     * <p>This is what lets a location objective answer "would I already be satisfied?" while its quest is
     * merely being <em>offered</em> and no {@code ActiveQuest} exists yet, so a quest whose destination
     * has already been reached is never offered in the first place.
     */
    public Optional<Resolved> resolveTarget(ServerPlayer player, @Nullable Entity giver, ServerLevel level) {
        return switch (type) {
            case HOME_VILLAGE -> Optional.ofNullable(giver).flatMap(g ->
                    McaCompat.getHomeVillageCenter(g).map(c -> new Resolved(c, McaCompat.getHomeVillageId(g))));
            case NEAREST_VILLAGE -> {
                BlockPos from = giver != null ? giver.blockPosition() : player.blockPosition();
                OptionalInt id = McaCompat.findNearestVillageId(level, from, radius.orElse(DEFAULT_NEAREST_RADIUS));
                yield id.isPresent()
                        ? McaCompat.villageCenter(level, id.getAsInt()).map(c -> new Resolved(c, id))
                        : Optional.empty();
            }
            case GIVER_POS -> Optional.ofNullable(giver).map(Entity::blockPosition).map(Resolved::of);
            case VILLAGER -> villager.flatMap(v -> v.resolveFrom(player, giver, level))
                    .map(Entity::blockPosition).map(Resolved::of);
            case WORKSTATION -> Optional.ofNullable(giver).flatMap(McaCompat::getWorkstationPos).map(Resolved::of);
            case BED -> Optional.ofNullable(giver).flatMap(McaCompat::getHomePos).map(Resolved::of);
            case COORDS -> pos.map(Resolved::of);
            // Offer-time resolution of a frozen anchor: nothing is recorded, so this answers "is there
            // a candidate at all?" without committing the quest to one before it has been accepted.
            case TOWNSTEAD_BUILDING, NEAREST_OTHER_VILLAGE ->
                    choose(player, giver, level).map(FrozenLocation::resolved);
        };
    }

    public Component describe() {
        return switch (type) {
            case HOME_VILLAGE, NEAREST_VILLAGE -> Component.translatable("mcaquests.anchor.village");
            case NEAREST_OTHER_VILLAGE -> Component.translatable("mcaquests.anchor.other_village");
            case GIVER_POS -> Component.translatable("mcaquests.anchor.giver");
            case VILLAGER -> villager.map(VillagerTarget::describe)
                    .orElseGet(() -> Component.translatable("mcaquests.anchor.villager"));
            case WORKSTATION -> Component.translatable("mcaquests.anchor.workstation");
            case BED -> Component.translatable("mcaquests.anchor.home");
            case COORDS -> Component.translatable("mcaquests.anchor.location");
            case TOWNSTEAD_BUILDING -> Component.translatable("mcaquests.anchor.townstead_building",
                    dev.otectus.mcaquests.quest.TownsteadNames.building(buildingType.orElse("")),
                    effectiveMinimumLevel());
        };
    }

    /** Cross-field validation surfaced by the owning objective's validator. */
    public void validate(String prefix, List<String> errors) {
        if (type == Type.COORDS && pos.isEmpty()) {
            errors.add(prefix + " uses anchor 'coords' but has no 'pos'.");
        }
        if (type == Type.VILLAGER && villager.isEmpty()) {
            errors.add(prefix + " uses anchor 'villager' but has no 'villager'.");
        }
        if (type == Type.TOWNSTEAD_BUILDING) {
            if (buildingType.isEmpty() || buildingType.get().isBlank()) {
                errors.add(prefix + " uses anchor 'townstead_building' but has no 'building_type'.");
            } else if (!TownsteadBuildings.isKnownFamily(buildingType.get())) {
                errors.add(prefix + " uses anchor 'townstead_building' with unknown family '"
                        + buildingType.get() + "'; expected one of " + TownsteadBuildings.families() + ".");
            }
        }
        if (type != Type.TOWNSTEAD_BUILDING && buildingType.isPresent()) {
            errors.add(prefix + " sets 'building_type' on anchor '" + type.name().toLowerCase(Locale.ROOT)
                    + "', which ignores it.");
        }
        minimumLevel.ifPresent(level -> {
            if (level < 1) {
                errors.add(prefix + " has minimum_level " + level + " (must be >= 1).");
            }
        });
        radius.ifPresent(r -> {
            if (r <= 0) {
                errors.add(prefix + " has radius " + r + " (must be > 0).");
            }
        });
        villager.ifPresent(v -> v.validate(prefix + " villager", errors));
    }
}
