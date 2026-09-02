package dev.otectus.mcaquests.quest.guidance;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.OptionalInt;

/**
 * One place the player is being sent, right now — the single thing the HUD names and the world
 * marker stands on.
 *
 * <p>The mod could already answer "who" ({@code VillagerTargeted}) and "where, roughly"
 * ({@code LocationAnchor}), but neither answer ever reached the client as a position it could draw:
 * the only navigation aid was a line of text, and {@code QuestLogEntry.TargetHint} deliberately
 * dropped the dimension because "an arrow across dimensions would be a lie". This record keeps the
 * dimension precisely so that case can be told the truth instead — a target in the Nether produces a
 * {@link GuidanceKind#PORTAL} pointing at the way in, not an arrow to a place you cannot walk to.
 *
 * @param entityId    the target's network id when it is a loaded entity, so the client can follow it
 *                    between the server's once-a-second recomputes; empty for a fixed position
 * @param pos         where to draw: the entity's position when {@code entityId} is present, and the
 *                    whole answer when it is not
 * @param dimension   the dimension {@code pos} is in. The marker draws nothing when it differs from
 *                    the one the player is standing in — a beam there would point through bedrock —
 *                    but the tracker still names the dimension and the coordinates, because a
 *                    coordinate in another world is worth writing down
 * @param arriveRadius how close counts as arrived, so the marker can fade out instead of hanging in
 *                    the player's face once they are standing on it
 * @param approximate true when the position is a search result rather than a live reading, so the
 *                    client can say "about" and not promise precision
 * @param lastKnown   true when the position is where somebody was last known to live rather than
 *                    where they are. Distinct from {@link #approximate}: "about 400 blocks" is a
 *                    claim about precision, "last seen 400 blocks away" is a claim about age, and
 *                    the tracker has said both since before guidance existed. It arrived here when
 *                    {@code QuestLogEntry.TargetHint} — which carried a {@code lastKnown} flag, a
 *                    position and no dimension at all — was folded into this record
 */
public record GuidanceTarget(GuidanceKind kind, OptionalInt entityId, BlockPos pos,
                             ResourceKey<Level> dimension, Component label,
                             int arriveRadius, boolean approximate, boolean lastKnown) {

    /** How close a person counts as found. Deliberately small: you have to actually reach them. */
    public static final int ENTITY_ARRIVE_RADIUS = 3;

    /** A loaded entity, followed live by the client. */
    public static GuidanceTarget ofEntity(Entity entity, GuidanceKind kind, Component label) {
        return new GuidanceTarget(kind, OptionalInt.of(entity.getId()), entity.blockPosition(),
                entity.level().dimension(), label, ENTITY_ARRIVE_RADIUS, false, false);
    }

    /** A fixed position in {@code level}. */
    public static GuidanceTarget ofPos(BlockPos pos, ServerLevel level, GuidanceKind kind,
                                       Component label, int arriveRadius, boolean approximate) {
        return new GuidanceTarget(kind, OptionalInt.empty(), pos, level.dimension(), label,
                Math.max(1, arriveRadius), approximate, false);
    }

    /** A fixed position in a dimension the player may not currently be in. */
    public static GuidanceTarget ofPos(BlockPos pos, ResourceKey<Level> dimension, GuidanceKind kind,
                                       Component label, int arriveRadius, boolean approximate) {
        return new GuidanceTarget(kind, OptionalInt.empty(), pos, dimension, label,
                Math.max(1, arriveRadius), approximate, false);
    }

    /** The same target relabelled — used when a caller knows a better name than the objective did. */
    public GuidanceTarget withLabel(Component newLabel) {
        return new GuidanceTarget(kind, entityId, pos, dimension, newLabel, arriveRadius, approximate,
                lastKnown);
    }

    /**
     * The same target, marked as somebody's last known whereabouts rather than a live reading.
     *
     * <p>A separate factory rather than a parameter on {@link #ofPos}, so the eighteen call sites
     * that mean "here, now" keep reading as they did and only the one that means "here, last I heard"
     * has to say so.
     */
    public GuidanceTarget asLastKnown() {
        return new GuidanceTarget(kind, entityId, pos, dimension, label, arriveRadius, approximate,
                true);
    }

    public static void encode(FriendlyByteBuf buf, GuidanceTarget target) {
        buf.writeVarInt(target.kind.ordinal());
        buf.writeVarInt(target.entityId.orElse(-1));
        buf.writeBlockPos(target.pos); // one long: VarInt is unsigned-biased, so negatives cost 5B
        buf.writeResourceLocation(target.dimension.location());
        buf.writeComponent(target.label);
        buf.writeVarInt(target.arriveRadius);
        buf.writeBoolean(target.approximate);
        buf.writeBoolean(target.lastKnown);
    }

    public static GuidanceTarget decode(FriendlyByteBuf buf) {
        GuidanceKind kind = GuidanceKind.byOrdinal(buf.readVarInt());
        int id = buf.readVarInt();
        return new GuidanceTarget(kind,
                id < 0 ? OptionalInt.empty() : OptionalInt.of(id),
                buf.readBlockPos(),
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        buf.readResourceLocation()),
                buf.readComponent(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean());
    }
}
