package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.network.NetComponents;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
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
                              List<ProjectObjectiveLine> objectives) {

    public static void encode(RegistryFriendlyByteBuf buf, ProjectLogEntry entry) {
        buf.writeResourceLocation(entry.projectId);
        NetComponents.write(buf, entry.title);
        NetComponents.write(buf, entry.sponsorLabel);
        NetComponents.write(buf, entry.scopeLabel);
        NetComponents.write(buf, entry.phaseLabel);
        buf.writeCollection(entry.objectives, (b, v) -> ProjectObjectiveLine.encode((RegistryFriendlyByteBuf) b, v));
    }

    public static ProjectLogEntry decode(RegistryFriendlyByteBuf buf) {
        return new ProjectLogEntry(
                buf.readResourceLocation(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                buf.readCollection(ArrayList::new, b -> ProjectObjectiveLine.decode((RegistryFriendlyByteBuf) b)));
    }
}
