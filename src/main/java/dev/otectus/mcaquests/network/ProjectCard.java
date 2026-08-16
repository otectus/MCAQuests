package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One community project as shown at a sponsoring villager (spec 0.4.0). Carries the richer,
 * project-only display — scope, sponsor/village, phase, shared progress bars, rewards — so individual
 * quest cards stay simple. {@code projectId} drives the Contribute C2S packet.
 */
public record ProjectCard(ResourceLocation projectId,
                          Component title,
                          Component scopeLabel,
                          Component sponsorLabel,
                          Component phaseLabel,
                          Component dialogue,
                          List<ProjectObjectiveLine> objectives,
                          List<Component> rewards,
                          ProjectMenuStatus status) {

    public static void encode(FriendlyByteBuf buf, ProjectCard card) {
        buf.writeResourceLocation(card.projectId);
        buf.writeComponent(card.title);
        buf.writeComponent(card.scopeLabel);
        buf.writeComponent(card.sponsorLabel);
        buf.writeComponent(card.phaseLabel);
        buf.writeComponent(card.dialogue);
        buf.writeCollection(card.objectives, ProjectObjectiveLine::encode);
        buf.writeCollection(card.rewards, FriendlyByteBuf::writeComponent);
        buf.writeEnum(card.status);
    }

    public static ProjectCard decode(FriendlyByteBuf buf) {
        return new ProjectCard(
                buf.readResourceLocation(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readCollection(ArrayList::new, ProjectObjectiveLine::decode),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent),
                buf.readEnum(ProjectMenuStatus.class));
    }
}
