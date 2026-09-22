package dev.otectus.mcaquests.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
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

    public static void encode(RegistryFriendlyByteBuf buf, ProjectCard card) {
        buf.writeResourceLocation(card.projectId);
        NetComponents.write(buf, card.title);
        NetComponents.write(buf, card.scopeLabel);
        NetComponents.write(buf, card.sponsorLabel);
        NetComponents.write(buf, card.phaseLabel);
        NetComponents.write(buf, card.dialogue);
        buf.writeCollection(card.objectives, (b, v) -> ProjectObjectiveLine.encode((RegistryFriendlyByteBuf) b, v));
        buf.writeCollection(card.rewards, NetComponents::write);
        buf.writeEnum(card.status);
        buf.writeUtf(card.instanceKey);
        buf.writeVarLong(card.revision);
        buf.writeBoolean(card.buildArea);
    }

    public static ProjectCard decode(RegistryFriendlyByteBuf buf) {
        return new ProjectCard(
                buf.readResourceLocation(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                PacketCollections.readList(buf, b -> ProjectObjectiveLine.decode((RegistryFriendlyByteBuf) b)),
                PacketCollections.readList(buf, NetComponents::read),
                buf.readEnum(ProjectMenuStatus.class),
                buf.readUtf(),
                buf.readVarLong(),
                buf.readBoolean());
    }
}
