package dev.otectus.mcaquests.compat.mapatlases;

/** Small loading-stage capability handshake; never links a client or optional class. */
public final class AtlasHookState {
    private static volatile boolean preflight, applied, viewport, rendered;
    private static volatile String failure = "atlas hooks not installed";
    private static volatile boolean handApplied, handViewport, handRendered;
    private static volatile String handFailure = "held atlas hook not installed";
    private AtlasHookState() { }
    public static boolean preflight() { return preflight; }
    public static boolean applied() { return applied; }
    public static String failure() { return failure; }
    public static void preflightPassed() { preflight = true; failure = "awaiting transformation"; }
    public static void appliedSuccessfully() { applied = true; failure = ""; }
    public static void failed(String reason) { applied = false; failure = reason; }
    public static void observed(boolean glyph) { viewport = true; rendered |= glyph; }
    public static boolean viewportObserved() { return viewport; }
    public static boolean renderObserved() { return rendered; }
    public static boolean handApplied() { return handApplied; }
    public static String handFailure() { return handFailure; }
    public static void handAppliedSuccessfully() { handApplied = true; handFailure = ""; }
    public static void handFailed(String reason) { handApplied = false; handFailure = reason; }
    public static void observedHand(boolean glyph) { handViewport = true; handRendered |= glyph; }
    public static boolean handViewportObserved() { return handViewport; }
    public static boolean handRenderObserved() { return handRendered; }
    public static void resetObservations() { viewport = false; rendered = false; handViewport = false; handRendered = false; }
}
