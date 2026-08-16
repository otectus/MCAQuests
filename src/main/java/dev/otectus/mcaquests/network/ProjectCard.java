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
        NetComponents.write(buf, card.title);
        NetComponents.write(buf, card.scopeLabel);
        NetComponents.write(buf, card.sponsorLabel);
        NetComponents.write(buf, card.phaseLabel);
        NetComponents.write(buf, card.dialogue);
        buf.writeCollection(card.objectives, ProjectObjectiveLine::encode);
        buf.writeCollection(card.rewards, NetComponents::write);
        buf.writeEnum(card.status);
    }

    public static ProjectCard decode(FriendlyByteBuf buf) {
        return new ProjectCard(
                buf.readResourceLocation(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                buf.readCollection(ArrayList::new, ProjectObjectiveLine::decode),
                buf.readCollection(ArrayList::new, NetComponents::read),
                buf.readEnum(ProjectMenuStatus.class));
    }
}
