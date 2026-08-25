package dev.otectus.mcaquests.compat;

/**
 * The server's Townstead calendar (Townstead spec §2.3). One per server, not per villager, so the
 * evaluation cache holds a single instance for a whole pass.
 */
public record TownsteadCalendarView(
        String profileId,
        long worldDay,
        int epochYearOffset,
        String timeMode,
        int year,
        int month,
        int day,
        int dayOfYear,
        int dayOfWeek,
        String season) {
}
