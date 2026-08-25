package dev.otectus.mcaquests.compat;

import java.util.List;

/**
 * A villager's Townstead shift schedule (Townstead spec §2.3).
 *
 * <p>{@link #currentActivity()} is what the villager's brain is <em>actually</em> doing and
 * {@link #plannedActivity()} is what their shift table says they should be; the two diverge when
 * something has interrupted them, which is exactly what a "hold this state" objective needs to see.
 * Both are lowercase {@code work} / {@code meet} / {@code rest} / {@code idle}.
 */
public record TownsteadScheduleView(
        String mode,
        String templateId,
        boolean customShifts,
        boolean nonDefaultCustomShifts,
        int currentTickHour,
        int currentDisplayHour,
        int currentShiftOrdinal,
        String currentActivity,
        String plannedActivity,
        String currentTemplateId,
        List<Integer> shifts,
        List<String> weekDayTemplates) {

    public TownsteadScheduleView {
        shifts = List.copyOf(shifts);
        weekDayTemplates = List.copyOf(weekDayTemplates);
    }

    /** True when the villager is doing what their schedule planned for this hour. */
    public boolean onSchedule() {
        return currentActivity.equals(plannedActivity);
    }
}
