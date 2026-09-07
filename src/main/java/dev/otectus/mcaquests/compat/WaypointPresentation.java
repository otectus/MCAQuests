package dev.otectus.mcaquests.compat;

/** Immutable presentation from authorized guidance. Unknown vertical provenance stays unknown. */
public record WaypointPresentation(boolean approximate, boolean lastKnown, int arriveRadius,
                                   boolean primary, boolean reliableHeight, String questTitle,
                                   boolean readyToTurnIn) {
    public static final WaypointPresentation DEFAULT =
            new WaypointPresentation(false, false, 0, false, false, "", false);

    public WaypointPresentation {
        arriveRadius = Math.max(0, arriveRadius);
        questTitle = questTitle == null ? "" : questTitle;
    }
}
