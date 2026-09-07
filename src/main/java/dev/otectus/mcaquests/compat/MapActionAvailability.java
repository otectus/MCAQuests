package dev.otectus.mcaquests.compat;

/** Read-only, target-specific action result. Reason is a localized mcaquests.atlas.reason key. */
public record MapActionAvailability(boolean available, String reason) {
    public static MapActionAvailability ready() { return new MapActionAvailability(true, "ready"); }
    public static MapActionAvailability unavailable(String reason) {
        return new MapActionAvailability(false, reason);
    }
}
