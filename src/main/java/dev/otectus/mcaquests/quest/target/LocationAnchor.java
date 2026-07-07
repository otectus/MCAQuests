package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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
 * { "anchor": "giver_pos" }                               // the giver's current position
 * { "anchor": "villager", "villager": { "mode": "family", "relation": "spouse" } }
 * { "anchor": "workstation" }                             // the giver's job site
 * { "anchor": "bed" }                                     // the giver's home/bed position
 * { "anchor": "coords", "pos": [100, 64, -200] }
 * </pre>
 */
public record LocationAnchor(Type type, Optional<Integer> radius,
                             Optional<VillagerTarget> villager, Optional<BlockPos> pos) {

    public enum Type {
        HOME_VILLAGE, NEAREST_VILLAGE, GIVER_POS, VILLAGER, WORKSTATION, BED, COORDS
    }

    private static final int DEFAULT_NEAREST_RADIUS = 128;

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
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(LocationAnchor::pos)
    ).apply(instance, LocationAnchor::new));

    public static final Codec<LocationAnchor> CODEC = MAP_CODEC.codec();

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
     */
    public Optional<Resolved> resolveTarget(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return switch (type) {
            case HOME_VILLAGE -> giver(level, active).flatMap(g ->
                    McaCompat.getHomeVillageCenter(g).map(c -> new Resolved(c, McaCompat.getHomeVillageId(g))));
            case NEAREST_VILLAGE -> {
                BlockPos from = giver(level, active).map(Entity::blockPosition).orElseGet(player::blockPosition);
                OptionalInt id = McaCompat.findNearestVillageId(level, from, radius.orElse(DEFAULT_NEAREST_RADIUS));
                yield id.isPresent()
                        ? McaCompat.villageCenter(level, id.getAsInt()).map(c -> new Resolved(c, id))
                        : Optional.empty();
            }
            case GIVER_POS -> giver(level, active).map(Entity::blockPosition).map(Resolved::of);
            case VILLAGER -> villager.flatMap(v -> v.resolve(player, active, level))
                    .map(Entity::blockPosition).map(Resolved::of);
            case WORKSTATION -> giver(level, active).flatMap(McaCompat::getWorkstationPos).map(Resolved::of);
            case BED -> giver(level, active).flatMap(McaCompat::getHomePos).map(Resolved::of);
            case COORDS -> pos.map(Resolved::of);
        };
    }

    public Component describe() {
        return switch (type) {
            case HOME_VILLAGE, NEAREST_VILLAGE -> Component.translatable("mcaquests.anchor.village");
            case GIVER_POS -> Component.translatable("mcaquests.anchor.giver");
            case VILLAGER -> villager.map(VillagerTarget::describe)
                    .orElseGet(() -> Component.translatable("mcaquests.anchor.villager"));
            case WORKSTATION -> Component.translatable("mcaquests.anchor.workstation");
            case BED -> Component.translatable("mcaquests.anchor.home");
            case COORDS -> Component.translatable("mcaquests.anchor.location");
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
        radius.ifPresent(r -> {
            if (r <= 0) {
                errors.add(prefix + " has radius " + r + " (must be > 0).");
            }
        });
        villager.ifPresent(v -> v.validate(prefix + " villager", errors));
    }

    private static Optional<Entity> giver(ServerLevel level, ActiveQuest active) {
        return Optional.ofNullable(level.getEntity(active.villagerUuid()));
    }
}
