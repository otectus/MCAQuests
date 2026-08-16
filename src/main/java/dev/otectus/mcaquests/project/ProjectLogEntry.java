package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import net.minecraft.network.FriendlyByteBuf;
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

    public static void encode(FriendlyByteBuf buf, ProjectLogEntry entry) {
        buf.writeResourceLocation(entry.projectId);
        buf.writeComponent(entry.title);
        buf.writeComponent(entry.sponsorLabel);
        buf.writeComponent(entry.scopeLabel);
        buf.writeComponent(entry.phaseLabel);
        buf.writeCollection(entry.objectives, ProjectObjectiveLine::encode);
    }

    public static ProjectLogEntry decode(FriendlyByteBuf buf) {
        return new ProjectLogEntry(
                buf.readResourceLocation(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readCollection(ArrayList::new, ProjectObjectiveLine::decode));
    }
}
