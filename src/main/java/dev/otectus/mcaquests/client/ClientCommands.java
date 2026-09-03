package dev.otectus.mcaquests.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.client.map.ClientMapWaypointRegistry;
import dev.otectus.mcaquests.client.map.WaypointDiagnostics;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.ProbeStep;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

/**
 * {@code /mcaquestsclient}: the diagnostics that only this client can answer.
 *
 * <p>Waypoints are drawn by one player's own minimap. The old {@code /mcaquests debug waypoints} was a
 * server command, so on a dedicated server it had nothing to read at all, and in single-player it
 * reached JourneyMap and Xaero from the logical server thread — which neither mod documents as safe.
 * A client command has none of that problem: it is registered by the client, dispatched on the client
 * thread, and reads client state.
 *
 * <p>Two subcommands, and the split between them is deliberate. {@code status} reads and never writes,
 * so it is safe at any moment; {@code probe} proves the chain by using it, which means adding and
 * removing a real waypoint, and so happens only when somebody asks for it by name.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class ClientCommands {

    private ClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mcaquestsclient")
                .then(Commands.literal("waypoints")
                        .executes(ClientCommands::waypointStatus)
                        .then(Commands.literal("status").executes(ClientCommands::waypointStatus))
                        .then(Commands.literal("probe").executes(ClientCommands::waypointProbe))));
    }

    /** What every installed map backend is doing, without touching any of them. */
    private static int waypointStatus(CommandContext<CommandSourceStack> ctx) {
        List<Component> lines = WaypointDiagnostics.describe(QuestWaypointSync.lastReport(),
                ClientMapWaypointRegistry.backends(), QuestWaypointSync.lastSyncMillis(),
                System.currentTimeMillis());
        lines.forEach(line -> ctx.getSource().sendSuccess(() -> line, false));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Runs each backend's self-test and reports every step.
     *
     * <p>Ends by marking the map dirty: the probe writes and withdraws its own waypoint, so the next
     * tick puts the real ones back rather than leaving the map to catch up when guidance next changes.
     */
    private static int waypointProbe(CommandContext<CommandSourceStack> ctx) {
        if (ClientMapWaypointRegistry.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("mcaquests.command.waypoints.none_installed"), false);
            return 0;
        }
        for (MapWaypointBackend backend : ClientMapWaypointRegistry.backends()) {
            ctx.getSource().sendSuccess(() -> WaypointDiagnostics.backendLine(backend.status()), false);
            if (!backend.isUsable()) {
                ctx.getSource().sendSuccess(() -> Component.translatable(
                        "mcaquests.command.waypoints.state.not_bound",
                        String.join(", ", backend.status().missingMembers())), false);
                continue;
            }
            for (ProbeStep step : backend.probe()) {
                Component line = WaypointDiagnostics.probeStep(step);
                ctx.getSource().sendSuccess(() -> line, false);
            }
        }
        QuestWaypointSync.markDirty();
        return Command.SINGLE_SUCCESS;
    }
}
