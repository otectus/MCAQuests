package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.project.objective.ProjectObjectiveStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One shared objective row for a project card/log entry: the objective text, the shared current/target,
 * and this player's own contribution so far (spec 0.4.0).
 *
 * <p>Since 1.6.6 a row also carries its {@link ProjectObjectiveStatus} — so a paused, blocked or
 * not-yet-observed objective says so in words and a glyph rather than looking stuck — and the expanded
 * help the objective builds from the same predicates that grant credit: what counts, where, the next
 * useful action, and why it is blocked.
 */
public record ProjectObjectiveLine(Component label, int sharedCurrent, int required, int yourContribution,
                                   ProjectObjectiveStatus status, List<Component> details) {

    public ProjectObjectiveLine {
        details = List.copyOf(details);
    }

    /** A row with no help and a status read off the numbers. */
    public ProjectObjectiveLine(Component label, int sharedCurrent, int required, int yourContribution) {
        this(label, sharedCurrent, required, yourContribution,
                sharedCurrent >= required ? ProjectObjectiveStatus.SATISFIED : ProjectObjectiveStatus.IN_PROGRESS,
                List.of());
    }

    public static void encode(RegistryFriendlyByteBuf buf, ProjectObjectiveLine line) {
        NetComponents.write(buf, line.label);
        buf.writeVarInt(line.sharedCurrent);
        buf.writeVarInt(line.required);
        buf.writeVarInt(line.yourContribution);
        buf.writeVarInt(line.status.ordinal());
        buf.writeCollection(line.details, NetComponents::write);
    }

    public static ProjectObjectiveLine decode(RegistryFriendlyByteBuf buf) {
        return new ProjectObjectiveLine(NetComponents.read(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                ProjectObjectiveStatus.byOrdinal(buf.readVarInt()),
                PacketCollections.readList(buf, NetComponents::read));
    }
}
