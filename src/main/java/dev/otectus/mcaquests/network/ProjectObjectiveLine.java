package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * One shared objective row for a project card/log entry: the objective text, the shared current/target,
 * and this player's own contribution so far (spec 0.4.0).
 */
public record ProjectObjectiveLine(Component label, int sharedCurrent, int required, int yourContribution) {

    public static void encode(FriendlyByteBuf buf, ProjectObjectiveLine line) {
        buf.writeComponent(line.label);
        buf.writeVarInt(line.sharedCurrent);
        buf.writeVarInt(line.required);
        buf.writeVarInt(line.yourContribution);
    }

    public static ProjectObjectiveLine decode(FriendlyByteBuf buf) {
        return new ProjectObjectiveLine(buf.readComponent(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
