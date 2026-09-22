package dev.otectus.mcaquests.project.objective;

import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.project.state.ProjectState;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * A polled project objective that reads Townstead state, together with the Townstead capabilities it
 * cannot work without.
 *
 * <p>Declared rather than inferred from the type id, because the loader has to decide before a project
 * is ever offered whether <em>any</em> of its phases needs Townstead ({@code IntegrationRequirements}).
 * A project whose first phase is a plain donation and whose second phase counts Townstead buildings is
 * a Townstead project, and a base installation must never offer it.
 */
public interface TownsteadProjectObjective extends PollingProjectObjective {

    /** Every Townstead capability {@link #poll} reads. */
    Set<TownsteadCapability> requiredCapabilities();

    /** Village-bound, and every declared capability bound on the running Townstead. */
    @Override
    default boolean isAvailable(ServerLevel level, ProjectState state) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (state.villageId().isEmpty() || !bridge.isAvailable()) {
            return false;
        }
        for (TownsteadCapability capability : requiredCapabilities()) {
            if (!bridge.has(capability)) {
                return false;
            }
        }
        return true;
    }
}
