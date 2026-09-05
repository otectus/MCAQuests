package dev.otectus.mcaquests.compat.iceandfire;

import dev.otectus.mcaquests.compat.CompatCapability;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The words {@code /mcaquests compat iceandfire status} prints, kept out of {@link IceAndFireCompat}
 * so that class stays a probe and nothing else.
 *
 * <p>The one piece of judgement here is the Myrmex line. Community Edition removed the hive, so
 * reporting {@code myrmex: MISSING} on a healthy CE install invites a bug report about a bug that
 * does not exist; the line says "expected for CE" instead. That is a presentation decision only —
 * the capability itself is still decided by the registry, so a CE build that re-adds the Myrmex
 * reports it available with no change here.
 */
public final class IceAndFireDiagnostics {

    private IceAndFireDiagnostics() {
    }

    /** Version, flavour, class probe, one line per capability, and the integration mode. */
    public static List<Component> lines(IceAndFireCompat compat) {
        List<Component> lines = new ArrayList<>();
        String version = compat.detectedVersion();
        if (!version.isEmpty()) {
            lines.add(Component.translatable("mcaquests.command.compat.iceandfire.version", version));
        }
        lines.add(Component.translatable("mcaquests.command.compat.iceandfire.flavor",
                flavorLabel(compat.flavor())));
        lines.add(Component.translatable("mcaquests.command.compat.iceandfire.class_probe",
                classProbeLabel(compat.flavor())));

        boolean communityEdition = compat.flavor() == IceAndFireFlavor.COMMUNITY_EDITION;
        for (CompatCapability capability : compat.capabilities()) {
            lines.add(capabilityLine(capability, communityEdition));
        }
        lines.add(Component.translatable("mcaquests.command.compat.iceandfire.mode"));
        return List.copyOf(lines);
    }

    private static Component capabilityLine(CompatCapability capability, boolean communityEdition) {
        if (capability.present()) {
            return Component.translatable("mcaquests.command.compat.iceandfire.capability.ok",
                    capability.id());
        }
        if (communityEdition && IceAndFireCapabilities.MYRMEX.equals(capability.id())) {
            return Component.translatable("mcaquests.command.compat.iceandfire.capability.expected_ce",
                    capability.id());
        }
        if (capability.detail().isEmpty()) {
            return Component.translatable("mcaquests.command.compat.iceandfire.capability.missing",
                    capability.id());
        }
        return Component.translatable("mcaquests.command.compat.iceandfire.capability.missing_detail",
                capability.id(), capability.detail());
    }

    private static Component flavorLabel(IceAndFireFlavor flavor) {
        return switch (flavor) {
            case COMMUNITY_EDITION -> Component.translatable("mcaquests.compat.iceandfire.ce");
            case ORIGINAL -> Component.translatable("mcaquests.compat.iceandfire.original");
            case AMBIGUOUS -> Component.translatable("mcaquests.command.compat.iceandfire.flavor.ambiguous");
            case NONE -> Component.translatable("mcaquests.command.compat.iceandfire.flavor.none");
        };
    }

    /**
     * Which entry-point class answered. Printed literally — this is the one line whose whole value is
     * being the exact string a bug report can be searched for, so it is never translated.
     */
    private static Component classProbeLabel(IceAndFireFlavor flavor) {
        return switch (flavor) {
            case COMMUNITY_EDITION -> Component.literal(IceAndFireFlavor.CE_CLASS);
            case ORIGINAL -> Component.literal(IceAndFireFlavor.ORIGINAL_CLASS);
            case AMBIGUOUS -> Component.literal(
                    IceAndFireFlavor.CE_CLASS + ", " + IceAndFireFlavor.ORIGINAL_CLASS);
            case NONE -> Component.translatable("mcaquests.command.compat.iceandfire.class_probe.none");
        };
    }
}
