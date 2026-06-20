package dev.otectus.mcaquests.network;

/** The state of a project shown in the villager menu (spec 0.4.0). Drives which buttons the screen shows. */
public enum ProjectMenuStatus {
    /** Not yet started — the sponsor is offering to begin it. */
    OFFER,
    /** Active and accepting contributions. */
    IN_PROGRESS,
    /** Finished. */
    COMPLETE
}
