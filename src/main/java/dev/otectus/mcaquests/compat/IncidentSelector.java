package dev.otectus.mcaquests.compat;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * A bounded, server-resolved description of which incident a quest condition or reward is talking
 * about (spec §29.6), in Minecraft and Java types only.
 *
 * <p>Deliberately not a free-form query. A client never picks an incident, and a datapack cannot ask
 * for "any incident" and have one chosen arbitrarily: {@link #isEmpty()} is checked before a resolve
 * is attempted, and the backend orders matches deterministically (newest first, incident id as
 * tiebreak) so the same selector against the same ledger always names the same record.
 */
public record IncidentSelector(List<ResourceLocation> types, List<String> statuses, List<String> tags,
                               boolean knownToGiver, long maxAgeTicks) {

    public static final IncidentSelector ANY = new IncidentSelector(List.of(), List.of(), List.of(), false, 0L);

    public IncidentSelector {
        types = types == null ? List.of() : List.copyOf(types);
        statuses = statuses == null ? List.of()
                : List.copyOf(statuses.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
        tags = tags == null ? List.of()
                : List.copyOf(tags.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
    }

    public static IncidentSelector ofType(ResourceLocation type) {
        return new IncidentSelector(List.of(type), List.of(), List.of(), false, 0L);
    }

    /** True when this selector narrows nothing — refused by {@code resolve_incident} (§29.6). */
    public boolean isEmpty() {
        return types.isEmpty() && statuses.isEmpty() && tags.isEmpty() && maxAgeTicks <= 0L;
    }
}
