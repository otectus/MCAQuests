package dev.otectus.mcaquests.compat;

/**
 * The MCA: Reputation capability strings this build knows how to use, as plain strings.
 *
 * <p>These are deliberately literals rather than references to {@code ReputationCapabilities}'
 * constants. This class is always loaded — the bridge, the conditions and the rewards name these ids
 * on every installation, with or without Reputation — and the whole point of the
 * {@code compat.reputation} seam is that no always-loaded class may name a type from a mod that might
 * not be installed (spec §29.1). A string that happens to match one over there is safe; a field read
 * from a class over there is a {@code NoClassDefFoundError}.
 *
 * <h2>Why capability strings replaced reflection</h2>
 *
 * <p>Until 1.6.5 the backend asked {@code McaReputationApi.class.getMethod("getVillagerOpinion", ...)}
 * to find out whether the installed Reputation had the opinion API. That worked, but it answers the
 * wrong question: a method exists in a binary whether or not the feature behind it can currently
 * answer. MCA: Reputation 0.5.0 added {@code capabilities(server).features()} precisely so a companion
 * stops guessing — the five 0.6.0 profile rows in particular appear <b>only while profiles are live</b>
 * (enabled, and with a datapack generation that published profile content), because a query that
 * cannot answer must never look like a negative answer about the player.
 */
public final class ReputationFeatures {

    private ReputationFeatures() {
    }

    // 0.5.0 operation features. Present in every 0.5.0-or-later build whatever the config says.

    /** {@code deliver(IncidentDelivery)} with typed receipt outcomes. */
    public static final String DELIVERY = "delivery";
    /** Read-only {@code findIncident}/{@code findReceipt} lookups. */
    public static final String READ_ONLY_LOOKUP = "read_only_lookup";
    /** Speaker-scoped selection, so {@code known_to_giver} can actually be evaluated. */
    public static final String SPEAKER_QUERY = "speaker_query";
    /** {@code resolveBound}: resolve an exact incident id, idempotent per operation key. */
    public static final String BOUND_RESOLUTION = "bound_resolution";
    /** {@code highWaterTierId(..., ladder)} for an arbitrary ladder. */
    public static final String LADDER_HIGH_WATER = "ladder_high_water";
    /** Exactly-once receipts behind a delivery's operation key. */
    public static final String RECEIPTS = "receipts";

    // 0.6.0 profile features. Advertised only while the feature can really answer.

    /** Community profile snapshots and {@code matchesProfile}. */
    public static final String PROFILE_SNAPSHOT = "profile_snapshot_v1";
    /** Knowledge-filtered villager profiles and {@code matchesSpeakerProfile}. */
    public static final String SPEAKER_PROFILE = "speaker_profile_v1";
    /** Repeat-credit policies over authored profiles. */
    public static final String REPEAT_CREDIT = "repeat_credit_v1";
    /** {@code deliverProfiled}: a delivery carrying an authored profile selection. */
    public static final String PROFILED_DELIVERY = "profiled_delivery_v1";
    /** The post-commit profile-changed event. */
    public static final String PROFILE_CHANGE = "profile_change_v1";
}
