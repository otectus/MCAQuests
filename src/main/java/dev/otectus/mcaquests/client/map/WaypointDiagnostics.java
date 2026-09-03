package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.BindingState;
import dev.otectus.mcaquests.compat.MapBackendCapabilities;
import dev.otectus.mcaquests.compat.MapBackendStatus;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.PinSupport;
import dev.otectus.mcaquests.compat.ProbeStep;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns the map layer's state into lines a player can read, and nothing else.
 *
 * <p>Pure on purpose. The diagnostic it replaces was a server command that added a waypoint in order
 * to discover whether it could, printed hard-coded English, and could therefore neither be translated
 * nor asserted on. Here the reading is separated from the doing: {@link #describe} touches no map at
 * all, and the one part that does write — the probe — is a separate subcommand whose <em>output</em> is
 * still rendered here.
 *
 * <p>Every string is a translation key. The only values interpolated are numbers, ids and the text a
 * third-party mod itself produced.
 */
public final class WaypointDiagnostics {

    private static final String PREFIX = "mcaquests.command.waypoints.";

    private WaypointDiagnostics() {
    }

    /**
     * The whole status report.
     *
     * @param report         the last reconciliation pass, for what config and backoff decided
     * @param backends       every installed backend, so one that has never been reconciled still appears
     * @param lastSyncMillis when the last pass succeeded, or 0 when none has
     * @param nowMillis      wall clock, so "seconds ago" is computed here rather than guessed at
     */
    public static List<Component> describe(SyncReport report, Collection<MapWaypointBackend> backends,
                                           long lastSyncMillis, long nowMillis) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(PREFIX + "header", report.worldEpoch(),
                report.guidanceRevision()).withStyle(ChatFormatting.GOLD));
        if (backends.isEmpty()) {
            lines.add(Component.translatable(PREFIX + "none_installed"));
            return lines;
        }
        for (MapWaypointBackend backend : backends) {
            MapBackendStatus status = backend.status();
            lines.add(backendLine(status));
            Optional<SyncReport.BackendReport> pass = report.backends().stream()
                    .filter(b -> b.id().equals(status.id()))
                    .findFirst();
            lines.add(stateLine(status, pass));
            lines.add(capabilitiesLine(status.capabilities()));
            lines.add(lastSyncLine(lastSyncMillis, nowMillis));
            pass.flatMap(SyncReport.BackendReport::nextRetryAtMillis).ifPresent(retryAt ->
                    lines.add(Component.translatable(PREFIX + "pending_retry",
                            seconds(retryAt - nowMillis))));
            status.lastFailure().ifPresent(failure ->
                    lines.add(Component.translatable(PREFIX + "last_failure", failure.fingerprint(),
                            failure.message().orElse(failure.result().name()))
                            .withStyle(ChatFormatting.RED)));
        }
        return lines;
    }

    /** "journeymap 6.0.4", or the id and an admission that the version could not be read. */
    public static Component backendLine(MapBackendStatus status) {
        // The version is the map mod's own string, so it is interpolated as data; only the case where
        // there isn't one needs words, and those come from the language file like everything else.
        Object version = status.modVersion().isPresent()
                ? status.modVersion().get()
                : Component.translatable(PREFIX + "version_unknown");
        return Component.translatable(PREFIX + "backend", status.id(), version)
                .withStyle(ChatFormatting.AQUA);
    }

    /** One line of a probe run: the step's stable name, whether it passed, and any detail it gave. */
    public static Component probeStep(ProbeStep step) {
        Component outcome = Component.translatable(PREFIX + (step.passed() ? "probe.passed" : "probe.failed"))
                .withStyle(step.passed() ? ChatFormatting.GREEN : ChatFormatting.RED);
        return step.detail()
                .map(detail -> Component.translatable(PREFIX + "probe.step_detail", step.name(), outcome, detail))
                .orElseGet(() -> Component.translatable(PREFIX + "probe.step", step.name(), outcome));
    }

    private static Component stateLine(MapBackendStatus status,
                                       Optional<SyncReport.BackendReport> pass) {
        if (pass.isPresent() && !pass.get().enabled()) {
            return Component.translatable(PREFIX + "disabled").withStyle(ChatFormatting.GRAY);
        }
        if (status.binding() != BindingState.BOUND) {
            return Component.translatable(PREFIX + "state.not_bound",
                    String.join(", ", status.missingMembers())).withStyle(ChatFormatting.RED);
        }
        if (pass.flatMap(SyncReport.BackendReport::nextRetryAtMillis).isPresent()) {
            return Component.translatable(PREFIX + "state.retry_pending").withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable(PREFIX + "state.usable", status.appliedCount())
                .withStyle(ChatFormatting.GREEN);
    }

    private static Component capabilitiesLine(MapBackendCapabilities capabilities) {
        return Component.translatable(PREFIX + "capabilities",
                yesNo(capabilities.automaticWaypoints()),
                pins(capabilities.pins()),
                yesNo(capabilities.currentDimensionOnly()));
    }

    private static Component lastSyncLine(long lastSyncMillis, long nowMillis) {
        return lastSyncMillis == 0L
                ? Component.translatable(PREFIX + "last_sync.never")
                : Component.translatable(PREFIX + "last_sync", seconds(nowMillis - lastSyncMillis));
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(PREFIX + (value ? "yes" : "no"));
    }

    private static Component pins(PinSupport pins) {
        return Component.translatable(PREFIX + "pins." + pins.name().toLowerCase(Locale.ROOT));
    }

    /** Never negative, and never zero for something that has not happened yet. */
    private static long seconds(long millis) {
        return Math.max(0L, (millis + 999L) / 1000L);
    }
}
