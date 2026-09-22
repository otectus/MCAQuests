package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A participating project as shown in the quest log + HUD tracker (spec 0.4.0). Deliberately carries no
 * deadline or abandon affordance — projects are community-owned.
 */
public record ProjectLogEntry(ResourceLocation projectId,
                              Component title,
                              Component sponsorLabel,
                              Component scopeLabel,
                              Component phaseLabel,
                              List<ProjectObjectiveLine> objectives,
                              String instanceKey,
                              java.util.Optional<Component> pausedReason) {

    /**
     * The pre-1.7.0 shape. {@code pausedReason} is present for a project the player is part of whose
     * definition is not loaded because an optional mod it needs is missing: it stays in the log, paused
     * and named, instead of vanishing until the mod returns.
     */
    public ProjectLogEntry(ResourceLocation projectId, Component title, Component sponsorLabel,
                           Component scopeLabel, Component phaseLabel, List<ProjectObjectiveLine> objectives) {
        this(projectId, title, sponsorLabel, scopeLabel, phaseLabel, objectives, "", java.util.Optional.empty());
    }

    public static void encode(FriendlyByteBuf buf, ProjectLogEntry entry) {
        buf.writeResourceLocation(entry.projectId);
        buf.writeComponent(entry.title);
        buf.writeComponent(entry.sponsorLabel);
        buf.writeComponent(entry.scopeLabel);
        buf.writeComponent(entry.phaseLabel);
        buf.writeCollection(entry.objectives, ProjectObjectiveLine::encode);
        buf.writeUtf(entry.instanceKey);
        buf.writeOptional(entry.pausedReason, FriendlyByteBuf::writeComponent);
    }

    public static ProjectLogEntry decode(FriendlyByteBuf buf) {
        return new ProjectLogEntry(
                buf.readResourceLocation(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                dev.otectus.mcaquests.network.PacketCollections.readList(buf, ProjectObjectiveLine::decode),
                buf.readUtf(),
                buf.readOptional(FriendlyByteBuf::readComponent));
    }
}
