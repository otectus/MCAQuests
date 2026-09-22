package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
                          ProjectMenuStatus status,
                          String instanceKey,
                          long revision,
                          boolean buildArea) {

    /**
     * The pre-1.7.0 shape. {@code instanceKey} names the live instance behind a card (empty for an
     * offer nobody has started), {@code revision} lets the client drop a card older than one it has
     * already drawn, and {@code buildArea} says whether the current phase has work that counts by
     * position, so the card offers to show where.
     */
    public ProjectCard(ResourceLocation projectId, Component title, Component scopeLabel, Component sponsorLabel,
                       Component phaseLabel, Component dialogue, List<ProjectObjectiveLine> objectives,
                       List<Component> rewards, ProjectMenuStatus status) {
        this(projectId, title, scopeLabel, sponsorLabel, phaseLabel, dialogue, objectives, rewards, status, "", 0L,
                false);
    }

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
        buf.writeUtf(card.instanceKey);
        buf.writeVarLong(card.revision);
        buf.writeBoolean(card.buildArea);
    }

    public static ProjectCard decode(FriendlyByteBuf buf) {
        return new ProjectCard(
                buf.readResourceLocation(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readComponent(),
                PacketCollections.readList(buf, ProjectObjectiveLine::decode),
                PacketCollections.readList(buf, FriendlyByteBuf::readComponent),
                buf.readEnum(ProjectMenuStatus.class),
                buf.readUtf(),
                buf.readVarLong(),
                buf.readBoolean());
    }
}
