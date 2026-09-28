package dev.otectus.mcaquests.compat.reputation;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.compat.ReputationAward;
import dev.otectus.mcaquests.compat.ReputationBackend;
import dev.otectus.mcaquests.compat.ReputationBridge;
import dev.otectus.mcaquests.compat.ReputationDeliveryResult;
import dev.otectus.mcaquests.compat.ReputationFeatures;
import dev.otectus.mcaquests.compat.ReputationProfileMatch;
import dev.otectus.mcaquests.compat.ReputationProfileQuery;
import dev.otectus.mcaquests.compat.VillagerOpinionView;
import dev.otectus.mcaquests.state.QuestCapabilities;
import dev.otectus.mcareputation.api.DeliveryOutcome;
import dev.otectus.mcareputation.api.IncidentDelivery;
import dev.otectus.mcareputation.api.IncidentQuery;
import dev.otectus.mcareputation.api.McaReputationApi;
import dev.otectus.mcareputation.api.ReceiptOutcome;
import dev.otectus.mcareputation.api.ReputationCapabilities;
import dev.otectus.mcareputation.api.ReputationIncidentView;
import dev.otectus.mcareputation.api.ReputationRequest;
import dev.otectus.mcareputation.api.ReputationResult;
import dev.otectus.mcareputation.api.ResolutionResult;
import dev.otectus.mcareputation.api.SpeakerContext;
import dev.otectus.mcareputation.api.profile.ProfileAvailability;
import dev.otectus.mcareputation.api.profile.ProfileQuery;
import dev.otectus.mcareputation.api.profile.ProfileQueryResult;
import dev.otectus.mcareputation.api.profile.ProfiledDelivery;
import dev.otectus.mcareputation.api.profile.ProfiledDeliveryResult;
import dev.otectus.mcareputation.community.CommunityKey;
import dev.otectus.mcareputation.incident.IncidentStatus;
import dev.otectus.mcareputation.incident.IncidentSubject;
import dev.otectus.mcareputation.incident.IncidentVisibility;
import dev.otectus.mcareputation.reputation.ReputationTierSet;
import dev.otectus.mcareputation.reputation.ReputationTiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The MCA: Reputation-backed implementation of {@link ReputationBackend} (spec §29.1).
 *
 * <p><b>This class is only ever loaded after {@code ModList.get().isLoaded("mcareputation")}.</b>
 * {@link ReputationBridge} constructs it reflectively for exactly that reason: a direct reference
 * would put every {@code mcareputation} type it names into the bridge's constant pool, and the bridge
 * has to load on installations where those classes do not exist.
 *
 * <p>Everything here is a translation layer and nothing more. Quests does not decide what a deed is
 * worth, when a tier changes, or whether a title is new — MCA: Reputation's transaction funnel owns
 * all of that (§10). Quests decides only <em>which</em> deed happened and to whom, which is the part
 * it actually knows.
 */
public final class CanonicalReputationBackend implements ReputationBackend {

    private final boolean compatible;

    public CanonicalReputationBackend() {
        // A future Reputation with a breaking API bumps its version; refusing here turns what would be
        // a NoSuchMethodError deep inside a quest turn-in into one clear log line at startup.
        int version = McaReputationApi.getApiVersion();
        this.compatible = version == ReputationBridge.REQUIRED_API_VERSION;
        if (!compatible) {
            McaQuests.LOGGER.error("[MCA: Quests] MCA: Reputation reports API v{} but this build was "
                    + "written against v{}.", version, ReputationBridge.REQUIRED_API_VERSION);
        }
        verifyFeatureStrings();
    }

    /**
     * Checks the always-loaded side's copies of Reputation's capability strings still match Reputation.
     *
     * <p>{@link ReputationFeatures} holds plain literals because it loads with MCA: Reputation absent,
     * and a literal cannot be checked by the compiler. This is the one class where both sides are on the
     * classpath at once, so it is the only place the comparison can be made — and a mismatch has to be
     * loud, because its symptom is a feature silently treated as unsupported. MCA: Crime
     * ({@code verifyMirroredNames}) and MCA: Conversations ({@code featureStringsAgree}) make the same
     * check; until now Quests was the one consumer that did not.
     */
    private static void verifyFeatureStrings() {
        List<String> drift = new java.util.ArrayList<>();
        compare(drift, ReputationFeatures.DELIVERY, ReputationCapabilities.FEATURE_DELIVERY);
        compare(drift, ReputationFeatures.READ_ONLY_LOOKUP, ReputationCapabilities.FEATURE_READ_ONLY_LOOKUP);
        compare(drift, ReputationFeatures.SPEAKER_QUERY, ReputationCapabilities.FEATURE_SPEAKER_QUERY);
        compare(drift, ReputationFeatures.BOUND_RESOLUTION, ReputationCapabilities.FEATURE_BOUND_RESOLUTION);
        compare(drift, ReputationFeatures.LADDER_HIGH_WATER, ReputationCapabilities.FEATURE_LADDER_HIGH_WATER);
        compare(drift, ReputationFeatures.RECEIPTS, ReputationCapabilities.FEATURE_RECEIPTS);
        compare(drift, ReputationFeatures.PROFILE_SNAPSHOT, ReputationCapabilities.FEATURE_PROFILE_SNAPSHOT);
        compare(drift, ReputationFeatures.SPEAKER_PROFILE, ReputationCapabilities.FEATURE_SPEAKER_PROFILE);
        compare(drift, ReputationFeatures.REPEAT_CREDIT, ReputationCapabilities.FEATURE_REPEAT_CREDIT);
        compare(drift, ReputationFeatures.PROFILED_DELIVERY, ReputationCapabilities.FEATURE_PROFILED_DELIVERY);
        compare(drift, ReputationFeatures.PROFILE_CHANGE, ReputationCapabilities.FEATURE_PROFILE_CHANGE);
        if (!drift.isEmpty()) {
            McaQuests.LOGGER.error("[MCA: Quests] the MCA: Reputation capability strings this build mirrors "
                    + "have drifted: {}. The integration still runs, but the affected features are treated "
                    + "as unsupported. This is a bug in MCA: Quests, not a misconfiguration.", drift);
        }
    }

    private static void compare(List<String> drift, String ours, String theirs) {
        if (!ours.equals(theirs)) {
            drift.add("'" + ours + "' != '" + theirs + "'");
        }
    }

    @Override
    public boolean isCanonical() {
        return compatible;
    }

    @Override
    public String backendName() {
        return "mcareputation:canonical";
    }

    private static Optional<CommunityKey> key(ResourceLocation dimension, int villageId) {
        return CommunityKey.of(dimension, villageId);
    }

    // ------------------------------------------------------------------
    // Capability negotiation (§14.4)
    // ------------------------------------------------------------------

    /**
     * The capability set of the Reputation this server is running with, resolved once and remembered.
     *
     * <p>This replaces the {@code getMethod} probes 1.6.5 used, and it is a better question in two
     * ways. A method existing in a binary says nothing about whether the feature behind it can answer
     * now: Reputation advertises the five 0.6.0 profile rows <b>only while profiles are live</b>
     * (enabled, and with published profile content), precisely so a companion does not mistake "cannot
     * say" for "no". And the capability set answers for everything at once, so adopting the next
     * additive method does not mean another reflective probe.
     *
     * <p>Scoped to one server and cleared by {@link #forgetCapabilities()} on shutdown and on every
     * datapack reload — a reload can publish or withdraw the profile content the profile rows depend
     * on, and readiness cached past that point would be a lie in whichever direction the pack moved.
     */
    private static volatile MinecraftServer capabilityServer;
    private static volatile ReputationCapabilities capabilities;
    private static volatile boolean capabilitiesProbed;

    /** Clears the cached capability snapshot. Called on server stop and on datapack reload. */
    public static synchronized void forgetCapabilities() {
        capabilityServer = null;
        capabilities = null;
        capabilitiesProbed = false;
    }

    private static synchronized ReputationCapabilities capabilities(@Nullable MinecraftServer server) {
        if (capabilitiesProbed && capabilityServer == server) {
            return capabilities;
        }
        ReputationCapabilities probed = null;
        try {
            probed = McaReputationApi.capabilities(server);
        } catch (Throwable t) {
            // An older Reputation without the entry point is a supported installation, not a fault;
            // every feature then reads as absent and each call site takes its documented fallback.
            McaQuests.LOGGER.debug("[MCA: Quests] MCA: Reputation reported no capability set; every "
                    + "optional feature will be treated as absent", t);
        }
        capabilities = probed;
        capabilityServer = server;
        capabilitiesProbed = true;
        if (probed != null) {
            McaQuests.LOGGER.debug("[MCA: Quests] MCA: Reputation capabilities: api v{}, enabled={}, "
                    + "features={}", probed.apiVersion(), probed.enabled(), probed.features());
        }
        return probed;
    }

    @Override
    public boolean supportsFeature(MinecraftServer server, String feature) {
        if (!compatible || feature == null) {
            return false;
        }
        ReputationCapabilities known = capabilities(server);
        // The API generation is checked here as well as in the constructor: a capability set from a
        // future generation names features whose contracts this build has not been written against.
        return known != null && known.apiVersion() == ReputationBridge.REQUIRED_API_VERSION
                && known.has(feature);
    }

    private boolean feature(@Nullable MinecraftServer server, String id) {
        return supportsFeature(server, id);
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Override
    public int score(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId) {
        return key(dimension, villageId)
                .map(community -> McaReputationApi.getScoreOrZero(server, player, community))
                .orElse(0);
    }

    @Override
    public String tierId(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                         ResourceLocation ladder) {
        return ReputationTiers.getOrDefault(ladder)
                .tierFor(score(server, player, dimension, villageId)).id();
    }

    @Override
    public int tierIndex(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                         ResourceLocation ladder) {
        ReputationTierSet set = ReputationTiers.getOrDefault(ladder);
        return set.indexOf(set.tierFor(score(server, player, dimension, villageId)).id());
    }

    @Override
    public Map<Integer, Integer> villageScores(MinecraftServer server, UUID player,
                                               ResourceLocation dimension) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (CommunityKey community : McaReputationApi.knownCommunities(server, player)) {
            if (community.dimension().equals(dimension)) {
                out.put(community.villageId(), McaReputationApi.getScoreOrZero(server, player, community));
            }
        }
        return out;
    }

    /**
     * The highest tier ever reached here <b>on the ladder that was asked about</b>.
     *
     * <p>Until 1.7.0 this ignored {@code ladder} and returned the snapshot's own high-water field,
     * which is the mark on Reputation's default ladder. A caller asking about a pack's custom ladder
     * therefore got an answer from a different ladder — usually a plausible-looking tier id that the
     * requested ladder does not even name, which a tier gate then reads as "never ranked". MCA:
     * Reputation 0.4.1 added the per-ladder lookup for exactly this; the snapshot field remains the
     * fallback for an older build, where it is at least right for the default ladder.
     */
    @Override
    public Optional<String> tierHighWater(MinecraftServer server, UUID player, ResourceLocation dimension,
                                          int villageId, ResourceLocation ladder) {
        Optional<CommunityKey> community = key(dimension, villageId);
        if (community.isEmpty()) {
            return Optional.empty();
        }
        if (ladder != null && feature(server, ReputationFeatures.LADDER_HIGH_WATER)) {
            return McaReputationApi.highWaterTierId(server, player, community.get(), ladder);
        }
        return McaReputationApi.getSnapshot(server, player, community.get())
                .flatMap(snapshot -> snapshot.highWaterTierId());
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    @Override
    public int award(ReputationAward award) {
        return deliver(award).newScore();
    }

    /**
     * The one write path, through MCA: Reputation's typed delivery (1.7.0).
     *
     * <p>Three defects in the 1.6.5 translation are fixed here together, because they are one
     * transaction:
     *
     * <ul>
     *   <li><b>The delta is passed through as authored.</b> {@code .delta(award.delta())} on an
     *       {@code int} turned "the author priced nothing" into "the author priced this at zero", so
     *       an incident's own {@code default_delta} could never apply through Quests. The award now
     *       carries an {@link OptionalInt} and Reputation's builder takes it unchanged.</li>
     *   <li><b>The operation is delivered, not merely recorded.</b> {@code deliver} answers with a
     *       receipt outcome, so a replay after a crash reports {@code DUPLICATE} against the settled
     *       answer instead of looking like a fresh application, and an accepted deed that produced no
     *       public incident stops being indistinguishable from a refusal.</li>
     *   <li><b>An authored social profile is honoured.</b> A deed whose meaning the incident id cannot
     *       carry — a completed commission versus a donated project — names its
     *       {@code incident_profile}, and this delivers it through {@code deliverProfiled} so the
     *       evidence half is chosen by the pack rather than guessed at (§9.5).</li>
     * </ul>
     *
     * <p>The two halves fail independently and deliberately: profiles being switched off must not stop
     * the deed being recorded or standing moving, so a refused profile is logged and the delivery
     * outcome still stands.
     */
    @Override
    public ReputationDeliveryResult deliver(ReputationAward award) {
        Optional<CommunityKey> community = key(award.dimension(), award.villageId());
        if (community.isEmpty()) {
            return ReputationDeliveryResult.unavailable();
        }
        ReputationRequest request = request(award, community.get());
        MinecraftServer server = award.server();

        // A keyed delivery needs an operation key; Quests' dedupe keys already name one logical
        // outcome each (ReputationDedupe), which is exactly the identity a receipt wants.
        String operationKey = award.dedupeKey() == null ? "" : award.dedupeKey();
        ResourceLocation profile = award.incidentProfile();

        if (profile != null && feature(server, ReputationFeatures.PROFILED_DELIVERY)) {
            return deliverProfiled(award, request, operationKey, profile);
        }
        if (profile != null) {
            McaQuests.LOGGER.debug("[MCA: Quests] this MCA: Reputation cannot carry the social profile "
                    + "{} right now; recording the deed without it", profile);
        }
        if (feature(server, ReputationFeatures.DELIVERY)) {
            IncidentDelivery delivery = IncidentDelivery.of(request, McaQuests.MOD_ID, operationKey);
            return translate(award, McaReputationApi.deliver(delivery), Optional.empty());
        }
        // A pre-0.4.1 Reputation has no delivery at all. The plain record still carries the dedupe
        // key, so the outcome is the same minus the receipt's memory of it.
        ReputationResult result = McaReputationApi.record(request);
        if (!result.applied() && result.reason() != ReputationResult.Reason.DUPLICATE) {
            McaQuests.LOGGER.debug("[MCA: Quests] reputation award for {} was not applied ({})",
                    award.player(), result.reason());
        }
        return new ReputationDeliveryResult(
                result.applied()
                        ? ReputationDeliveryResult.Status.APPLIED
                        : result.reason() == ReputationResult.Reason.DUPLICATE
                                ? ReputationDeliveryResult.Status.DUPLICATE
                                : ReputationDeliveryResult.Status.REFUSED_INVALID,
                result.newScore(), false, Optional.empty());
    }

    /**
     * The profiled half of {@link #deliver}, in its own method on purpose.
     *
     * <p>Every 0.6.0-only type this integration names lives in a method that is entered only after the
     * matching capability string said the feature is live. That keeps a Reputation 0.5.0 installation —
     * where {@code ProfiledDelivery} and friends simply do not exist — from ever resolving a reference
     * to them, rather than relying on which constant-pool entries the JVM happens to resolve lazily.
     */
    private ReputationDeliveryResult deliverProfiled(ReputationAward award, ReputationRequest request,
                                                     String operationKey, ResourceLocation profile) {
        IncidentDelivery delivery = IncidentDelivery.of(request, McaQuests.MOD_ID, operationKey);
        ProfiledDeliveryResult result = McaReputationApi.deliverProfiled(
                ProfiledDelivery.of(delivery, profile));
        if (result.profileAvailability() != ProfileAvailability.AVAILABLE) {
            // The two halves fail independently: a deed still records and still moves standing while
            // profiles are off, and losing the social half must never undo the deed (§16.3).
            McaQuests.LOGGER.debug("[MCA: Quests] the social profile {} was not applied to {}'s deed "
                    + "({}); the deed itself still stands", profile, award.player(),
                    result.profileAvailability());
        }
        return translate(award, result.outcome(), result.appliedProfile());
    }

    private ReputationRequest request(ReputationAward award, CommunityKey community) {
        ResourceLocation incidentType = award.incidentType() != null
                ? award.incidentType()
                // A reward with no authored incident type still deserves a named story rather than an
                // anonymous number, so it lands as the generic quest completion (§16).
                : ResourceLocation.fromNamespaceAndPath("mcareputation", "quest_completed");

        ReputationRequest.Builder request = ReputationRequest
                .builder(award.server(), award.player(), community, incidentType, award.source())
                // OptionalInt, not int: an omitted delta leaves the incident definition's own value,
                // and an authored zero stays an authored zero.
                .delta(award.delta())
                .dedupeKey(award.dedupeKey())
                .context(award.context());
        if (award.visibility() != null) {
            IncidentVisibility.byName(award.visibility()).ifPresent(request::visibility);
        }
        if (award.subjectUuid() != null || award.subjectName() != null) {
            request.subject(IncidentSubject.villager(award.subjectUuid(),
                    award.subjectName() == null ? "" : award.subjectName(), award.subjectRole()));
        }
        return request.build();
    }

    /** Reputation's receipt outcome in the plain vocabulary the always-loaded side understands. */
    private ReputationDeliveryResult translate(ReputationAward award, DeliveryOutcome outcome,
                                               Optional<ResourceLocation> appliedProfile) {
        if (outcome == null) {
            return ReputationDeliveryResult.unavailable();
        }
        ReceiptOutcome receipt = outcome.outcome();
        ReputationDeliveryResult.Status status = switch (receipt) {
            case APPLIED -> ReputationDeliveryResult.Status.APPLIED;
            case DUPLICATE -> ReputationDeliveryResult.Status.DUPLICATE;
            case ACCEPTED_NO_PUBLIC_INCIDENT ->
                    ReputationDeliveryResult.Status.ACCEPTED_NO_PUBLIC_INCIDENT;
            case REFUSED_DISABLED -> ReputationDeliveryResult.Status.REFUSED_DISABLED;
            case REFUSED_INVALID -> ReputationDeliveryResult.Status.REFUSED_INVALID;
            case REFUSED_CAPACITY -> ReputationDeliveryResult.Status.REFUSED_CAPACITY;
        };
        if (!status.accepted()) {
            McaQuests.LOGGER.debug("[MCA: Quests] reputation delivery for {} answered {} (retryable={})",
                    award.player(), receipt, outcome.retryable());
        }
        ReputationResult result = outcome.result();
        return new ReputationDeliveryResult(status, result == null ? 0 : result.newScore(),
                outcome.retryable(), appliedProfile);
    }

    @Override
    public boolean grantTitle(MinecraftServer server, UUID player, @Nullable ResourceLocation dimension,
                              int villageId, ResourceLocation title, boolean global) {
        if (global) {
            return McaReputationApi.grantTitle(server, player, title, null);
        }
        return dimension != null && key(dimension, villageId)
                .map(community -> McaReputationApi.grantTitle(server, player, title, community))
                .orElse(false);
    }

    /**
     * Reads are the union of Reputation's answer and the player's own {@code PlayerTitles}.
     *
     * <p>Quests still writes titles into its own per-player store — the Journal, the title conditions
     * and the tier ladder all read it — and those writes do not (yet) reach Reputation. Asking only
     * Reputation therefore made a title a quest had just granted invisible to the condition gating the
     * next quest, and made tier titles invisible to the Journal, on exactly the installs that have both
     * mods. The same union is what the legacy backend has always done. Writing quest titles into
     * Reputation is 1.6 work; this is the read half, and it cannot recurse.
     */
    @Override
    public boolean hasTitle(MinecraftServer server, UUID player, @Nullable ResourceLocation dimension,
                            int villageId, ResourceLocation title, boolean global) {
        Optional<CommunityKey> community = global || dimension == null
                ? Optional.empty()
                : key(dimension, villageId);
        if (McaReputationApi.hasTitle(server, player, title, community)) {
            return true;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) {
            return false;
        }
        return QuestCapabilities.get(online).map(data -> global
                ? data.titles().hasGlobal(title)
                : dimension != null && data.titles().hasVillage(dimension, villageId, title)).orElse(false);
    }

    @Override
    public Set<ResourceLocation> globalTitles(MinecraftServer server, UUID player) {
        Set<ResourceLocation> held = new LinkedHashSet<>();
        McaReputationApi.getAllSnapshots(server, player)
                .forEach(snapshot -> held.addAll(snapshot.globalTitles()));
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            QuestCapabilities.get(online).ifPresent(data -> held.addAll(data.titles().global()));
        }
        return held;
    }

    @Override
    public Set<ResourceLocation> villageTitles(MinecraftServer server, UUID player,
                                               ResourceLocation dimension, int villageId) {
        Set<ResourceLocation> held = new LinkedHashSet<>();
        key(dimension, villageId)
                .flatMap(community -> McaReputationApi.getSnapshot(server, player, community))
                .ifPresent(snapshot -> held.addAll(snapshot.villageTitles()));
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            QuestCapabilities.get(online)
                    .ifPresent(data -> held.addAll(data.titles().forVillage(dimension, villageId)));
        }
        return held;
    }

    // ------------------------------------------------------------------
    // Incidents
    // ------------------------------------------------------------------

    @Override
    public boolean hasIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                               int villageId, IncidentSelector selector) {
        return hasIncident(server, player, dimension, villageId, selector, null);
    }

    @Override
    public boolean hasIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                               int villageId, IncidentSelector selector, @Nullable Entity giver) {
        Optional<CommunityKey> community = key(dimension, villageId);
        if (community.isEmpty()) {
            return false;
        }
        IncidentQuery query = toQuery(selector, false);
        if (!selector.knownToGiver()) {
            return !McaReputationApi.selectIncidents(server, player, community.get(), query).isEmpty();
        }
        Optional<SpeakerContext> speaker = speaker(server, giver);
        if (speaker.isEmpty()) {
            // Fails closed, and that is the whole point of asking. "Does this villager know what you
            // did" has no community-wide answer; answering it from the village record would have a
            // stranger demand amends for something they never heard about (§13.3).
            McaQuests.LOGGER.debug("[MCA: Quests] a known_to_giver selector had no resolvable giver; "
                    + "answering that nothing is known");
            return false;
        }
        return !McaReputationApi
                .selectIncidents(server, player, community.get(), query, speaker.get()).isEmpty();
    }

    @Override
    public boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                   int villageId, IncidentSelector selector, String resolution,
                                   @Nullable String dedupeKey) {
        return resolveIncident(server, player, dimension, villageId, selector, resolution, dedupeKey,
                null);
    }

    /**
     * Resolves the incident a selector names, bound to an exact record and settled once (1.7.0).
     *
     * <p>Two changes from 1.6.5. The {@code dedupeKey} parameter is now actually used: with
     * {@code resolveBound} the discovered incident id is settled <em>under that key</em>, so a replay
     * after a crash answers from the receipt rather than re-running the selector and possibly
     * resolving a different deed than the reward was granted for (§16.1's "bind an exact incident
     * before settlement"). And a {@code known_to_giver} selector is evaluated against the giver's own
     * knowledge instead of being silently dropped.
     */
    @Override
    public boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                   int villageId, IncidentSelector selector, String resolution,
                                   @Nullable String dedupeKey, @Nullable Entity giver) {
        if (selector.isEmpty()) {
            McaQuests.LOGGER.warn("[MCA: Quests] refusing to resolve an incident with an empty selector; "
                    + "name at least a type, status, or tag");
            return false;
        }
        Optional<IncidentStatus> status = IncidentStatus.byName(resolution);
        if (status.isEmpty()) {
            McaQuests.LOGGER.warn("[MCA: Quests] unknown incident resolution '{}'", resolution);
            return false;
        }
        Optional<CommunityKey> community = key(dimension, villageId);
        if (community.isEmpty()) {
            return false;
        }
        Optional<SpeakerContext> speaker = speaker(server, giver);
        if (selector.knownToGiver() && speaker.isEmpty()) {
            McaQuests.LOGGER.debug("[MCA: Quests] refusing to resolve a known_to_giver selector with no "
                    + "resolvable giver; nothing is known to nobody");
            return false;
        }

        // Bind first, settle second, when both halves are available. The selector discovers the
        // incident; the key settles that one incident and no other.
        if (dedupeKey != null && !dedupeKey.isBlank()
                && feature(server, ReputationFeatures.BOUND_RESOLUTION)) {
            Optional<UUID> bound = bind(server, player, community.get(), selector, speaker);
            if (bound.isEmpty()) {
                return false;
            }
            ResolutionResult result = McaReputationApi.resolveBound(server, player, community.get(),
                    bound.get(), status.get(), QUESTS_SOURCE, dedupeKey);
            return result.applied();
        }

        ResolutionResult result = speaker.isPresent()
                ? McaReputationApi.resolveBySelector(server, player, community.get(),
                        toQuery(selector, true), speaker.get(), status.get(), QUESTS_SOURCE)
                : McaReputationApi.resolveBySelector(server, player, community.get(),
                        toQuery(selector, true), status.get(), QUESTS_SOURCE);
        return result.applied();
    }

    /** The one incident this selector names, read-only, or empty when it names none. */
    private Optional<UUID> bind(MinecraftServer server, UUID player, CommunityKey community,
                                IncidentSelector selector, Optional<SpeakerContext> speaker) {
        IncidentQuery query = toQuery(selector, true);
        List<ReputationIncidentView> found = speaker.isPresent()
                ? McaReputationApi.selectIncidents(server, player, community, query, speaker.get())
                : McaReputationApi.selectIncidents(server, player, community, query);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0).id());
    }

    /**
     * The speaker a giver stands for, or empty when there is no giver or Reputation cannot name one.
     *
     * <p>Reputation resolves residency itself from the loaded entity, which is the only place that
     * knows it; Quests must not invent it. Empty is also what a build without speaker-aware selection
     * answers, and every caller treats that as "cannot say" rather than "no".
     */
    private Optional<SpeakerContext> speaker(MinecraftServer server, @Nullable Entity giver) {
        if (giver == null || !feature(server, ReputationFeatures.SPEAKER_QUERY)) {
            return Optional.empty();
        }
        return McaReputationApi.speakerContext(server, giver);
    }

    @Override
    public boolean recordIncident(ReputationAward award) {
        ReputationDeliveryResult result = deliver(award);
        // A duplicate is a success from the caller's point of view — the deed is on the record — and an
        // accepted-but-private deed is too. Only a refusal is a failure.
        return result.accepted();
    }

    // ------------------------------------------------------------------
    // Per-villager opinion
    // ------------------------------------------------------------------

    /**
     * What one villager personally makes of the player.
     *
     * <p>1.6.5 asked {@code getMethod("getVillagerOpinion", ...)} whether the API existed at all.
     * That probe is gone: the capability set answers the same question better, because
     * {@code opinionEnabled} also reports the case the probe could not see — the method being present
     * while the feature is switched off in Reputation's own config, where every answer would be a
     * neutral zero that an opinion gate would read as a real, low opinion.
     */
    @Override
    public Optional<VillagerOpinionView> villagerOpinion(MinecraftServer server, UUID player, UUID villager,
                                                         ResourceLocation dimension, int villageId) {
        ReputationCapabilities known = capabilities(server);
        if (known == null || !known.enabled() || !known.opinionEnabled()) {
            return Optional.empty();
        }
        return key(dimension, villageId)
                .flatMap(community -> McaReputationApi.getVillagerOpinion(server, player, villager, community))
                .map(opinion -> new VillagerOpinionView(opinion.opinion(), opinion.tierId(),
                        opinion.basis().jsonName()));
    }

    // ------------------------------------------------------------------
    // Public profiles (0.6.0, §14.3 and §14.5)
    // ------------------------------------------------------------------

    /**
     * Whether an authored profile predicate holds, asked of the village or of the giver.
     *
     * <p>Every interesting property of this method is a refusal to guess. A missing profile feature is
     * {@code UNSUPPORTED} and a switched-off one {@code DISABLED}, neither of which is "no". A
     * {@code giver}-scoped query with no resolvable giver is {@code UNRESOLVED} — it never falls back
     * to the community profile, because a speaker gate that quietly became a village gate is how a
     * stranger gets treated as a friend (§13.3). And a structurally invalid query is
     * {@code UNRESOLVED} as well, so a pack's typo surfaces as "cannot say" and runs the authored
     * fallback rather than silently failing every eligibility pass.
     */
    @Override
    public ReputationProfileMatch matchesProfile(MinecraftServer server, UUID player,
                                                 ResourceLocation dimension, int villageId,
                                                 @Nullable Entity giver, ReputationProfileQuery query) {
        if (query == null || query.isEmpty() || !query.valid()) {
            return ReputationProfileMatch.unavailable(ReputationProfileMatch.Availability.UNRESOLVED,
                    "invalid_query");
        }
        boolean speakerScoped = query.scope() == ReputationProfileQuery.Scope.GIVER;
        String needed = speakerScoped
                ? ReputationFeatures.SPEAKER_PROFILE
                : ReputationFeatures.PROFILE_SNAPSHOT;
        if (!feature(server, needed)) {
            return notLive(server);
        }
        Optional<CommunityKey> community = key(dimension, villageId);
        if (community.isEmpty()) {
            return ReputationProfileMatch.unavailable(
                    ReputationProfileMatch.Availability.UNRESOLVED, "unresolved_community");
        }
        if (speakerScoped && giver == null) {
            // §13.3, and the whole reason this scope exists: "what does this villager know" has no
            // village-wide answer, so with nobody to ask the honest reply is "cannot say".
            return ReputationProfileMatch.unavailable(
                    ReputationProfileMatch.Availability.UNRESOLVED, "no_speaker_context");
        }
        try {
            return ask(server, player, community.get(), giver, query, speakerScoped);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] a profile predicate could not be evaluated", t);
            return ReputationProfileMatch.unavailable(ReputationProfileMatch.Availability.ERROR,
                    "internal_error");
        }
    }

    /**
     * The 0.6.0 profile call itself, in its own method for the same reason
     * {@link #deliverProfiled} is: every reference to a type that exists only in Reputation 0.6.0
     * sits behind the capability check that proved the feature is live.
     */
    private ReputationProfileMatch ask(MinecraftServer server, UUID player, CommunityKey community,
                                       @Nullable Entity giver, ReputationProfileQuery query,
                                       boolean speakerScoped) {
        ProfileQuery translated = toProfileQuery(query);
        ProfileQueryResult<Boolean> result;
        if (speakerScoped) {
            Optional<SpeakerContext> speaker = McaReputationApi.speakerContext(server, giver);
            result = speaker.isPresent()
                    // The community Quests resolved is the one the quest is about, which is not always
                    // the one the villager's own residency would resolve to; pass both.
                    ? McaReputationApi.matchesSpeakerProfile(server, player, community, speaker.get(),
                            translated)
                    : McaReputationApi.matchesSpeakerProfile(server, player, giver, translated);
        } else {
            result = McaReputationApi.matchesProfile(server, player, community, translated);
        }
        return translate(result);
    }

    /**
     * Why the profile layer cannot answer: switched off, or not there at all.
     *
     * <p>Worth telling apart. {@code DISABLED} says this Reputation can do profiles and currently is
     * not, which an operator can change; {@code UNSUPPORTED} says the installed build has no profile
     * layer, which they cannot. {@code profileCapabilities} is itself a 0.6.0 method, so a
     * {@code LinkageError} from an older build is the {@code UNSUPPORTED} answer rather than a fault —
     * this is the one place a call has to survive not existing, because it is the call that asks
     * whether anything else exists.
     */
    private static ReputationProfileMatch notLive(@Nullable MinecraftServer server) {
        try {
            return McaReputationApi.profileCapabilities(server).supported()
                    ? ReputationProfileMatch.unavailable(
                            ReputationProfileMatch.Availability.DISABLED, "profiles_not_live")
                    : ReputationProfileMatch.unsupported();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] this MCA: Reputation has no profile layer", t);
            return ReputationProfileMatch.unsupported();
        }
    }

    private static ProfileQuery toProfileQuery(ReputationProfileQuery query) {
        ProfileQuery.Builder builder = ProfileQuery.builder()
                .allowPartialHistory(query.allowPartialHistory());
        query.minRecognition().ifPresent(builder::minRecognition);
        query.maxRecognition().ifPresent(builder::maxRecognition);
        query.minRecognitionTier().ifPresent(builder::minRecognitionTier);
        for (ReputationProfileQuery.Facet facet : query.facets()) {
            builder.facet(new ProfileQuery.FacetPredicate(facet.facet(), facet.min(), facet.max(),
                    facet.minEvidence(), facet.allowUnobserved()));
        }
        return builder.build();
    }

    private static ReputationProfileMatch translate(ProfileQueryResult<Boolean> result) {
        if (result == null) {
            return ReputationProfileMatch.unavailable(ReputationProfileMatch.Availability.ERROR,
                    "no_result");
        }
        String reason = result.reason().orElse(null);
        if (result.isAvailable()) {
            return ReputationProfileMatch.available(Boolean.TRUE.equals(result.value().orElse(false)),
                    reason);
        }
        ReputationProfileMatch.Availability availability = switch (result.availability()) {
            case AVAILABLE, UNRESOLVED -> ReputationProfileMatch.Availability.UNRESOLVED;
            case DISABLED -> ReputationProfileMatch.Availability.DISABLED;
            case UNSUPPORTED -> ReputationProfileMatch.Availability.UNSUPPORTED;
            case READ_ONLY -> ReputationProfileMatch.Availability.READ_ONLY;
            case MIGRATING -> ReputationProfileMatch.Availability.MIGRATING;
            case INCOMPLETE_HISTORY -> ReputationProfileMatch.Availability.INCOMPLETE_HISTORY;
            case ERROR -> ReputationProfileMatch.Availability.ERROR;
        };
        return ReputationProfileMatch.unavailable(availability, reason);
    }

    private static IncidentQuery toQuery(IncidentSelector selector, boolean newestOnly) {
        IncidentQuery.Builder builder = IncidentQuery.builder()
                .types(selector.types())
                .tags(selector.tags())
                .knownToSpeaker(selector.knownToGiver())
                .maxAgeTicks(selector.maxAgeTicks())
                .newestOnly(newestOnly);
        for (String status : selector.statuses()) {
            IncidentStatus.byName(status).ifPresent(builder::status);
        }
        return builder.build();
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    @Override
    public boolean openStandingScreen(ServerPlayer player, ResourceLocation dimension, int villageId) {
        // Reputation sends a fresh snapshot ahead of the open on its own channel, so the screen never
        // shows a stale cache; the journal's server-side validation happened before we were called.
        return key(dimension, villageId)
                .map(community -> McaReputationApi.openReputationScreen(player, community))
                .orElse(false);
    }

    /** The producer id every Quests write carries. */
    private static final ResourceLocation QUESTS_SOURCE =
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quests");

    /** The incident types Quests creates, resolved once so call sites read clearly. */
    public static final class Incidents {

        private Incidents() {
        }

        public static final ResourceLocation QUEST_COMPLETED = rep("quest_completed");
        public static final ResourceLocation QUEST_FAILED = rep("quest_failed");
        public static final ResourceLocation QUEST_ABANDONED = rep("quest_abandoned");
        public static final ResourceLocation PROJECT_PHASE_COMPLETED = rep("project_phase_completed");
        public static final ResourceLocation PROJECT_COMPLETED = rep("project_completed");
        public static final ResourceLocation PROJECT_FAILED = rep("project_failed");
        public static final ResourceLocation SITUATION_RESOLVED = rep("situation_resolved");

        private static ResourceLocation rep(String path) {
            return ResourceLocation.fromNamespaceAndPath("mcareputation", path);
        }

        public static List<ResourceLocation> all() {
            return List.of(QUEST_COMPLETED, QUEST_FAILED, QUEST_ABANDONED, PROJECT_PHASE_COMPLETED,
                    PROJECT_COMPLETED, PROJECT_FAILED, SITUATION_RESOLVED);
        }
    }
}
