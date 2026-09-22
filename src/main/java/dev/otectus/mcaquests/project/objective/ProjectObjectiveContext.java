package dev.otectus.mcaquests.project.objective;

import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Everything an objective needs to explain itself for one instance, server side.
 *
 * @param level          the instance's own dimension, or null when it is not loaded
 * @param state          the live instance, or null for an offer that has not started
 * @param objectiveIndex the objective's index within {@code phaseIndex}
 * @param viewer         the player the explanation is for, or null for an operator diagnostic
 */
public record ProjectObjectiveContext(@Nullable MinecraftServer server, @Nullable ServerLevel level,
                                      ProjectDefinition definition, @Nullable ProjectState state,
                                      int phaseIndex, int objectiveIndex, SharedObjectiveProgress progress,
                                      @Nullable ServerPlayer viewer) {

    /** The bound village's name, or a generic "this village" when there is none or it cannot be read. */
    public net.minecraft.network.chat.Component villageName() {
        if (state != null && level != null && state.villageId().isPresent()) {
            return dev.otectus.mcaquests.compat.McaCompat.villageName(level, state.villageId().getAsInt())
                    .<net.minecraft.network.chat.Component>map(net.minecraft.network.chat.Component::literal)
                    .orElseGet(() -> net.minecraft.network.chat.Component.translatable("mcaquests.project.help.this_village"));
        }
        return net.minecraft.network.chat.Component.translatable("mcaquests.project.help.this_village");
    }

    /** True when the instance is bound to an MCA village rather than a bare anchor. */
    public boolean villageBound() {
        return state == null || state.villageId().isPresent();
    }
}
