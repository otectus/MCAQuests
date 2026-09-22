package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.network.NetComponents;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    public static void encode(RegistryFriendlyByteBuf buf, ProjectLogEntry entry) {
        buf.writeResourceLocation(entry.projectId);
        NetComponents.write(buf, entry.title);
        NetComponents.write(buf, entry.sponsorLabel);
        NetComponents.write(buf, entry.scopeLabel);
        NetComponents.write(buf, entry.phaseLabel);
        buf.writeCollection(entry.objectives, (b, v) -> ProjectObjectiveLine.encode((RegistryFriendlyByteBuf) b, v));
        buf.writeUtf(entry.instanceKey);
        buf.writeOptional(entry.pausedReason, NetComponents::write);
    }

    public static ProjectLogEntry decode(RegistryFriendlyByteBuf buf) {
        return new ProjectLogEntry(
                buf.readResourceLocation(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                dev.otectus.mcaquests.network.PacketCollections.readList(buf, b -> ProjectObjectiveLine.decode((RegistryFriendlyByteBuf) b)),
                buf.readUtf(),
                buf.readOptional(NetComponents::read));
    }
}
