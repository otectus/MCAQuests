package dev.otectus.mcaquests.reputation;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.compat.ReputationAward;
import dev.otectus.mcaquests.compat.ReputationBackend;
import dev.otectus.mcaquests.compat.ReputationBridge;
import dev.otectus.mcaquests.compat.ReputationDeliveryResult;
import dev.otectus.mcaquests.compat.ReputationFeatures;
import dev.otectus.mcaquests.compat.ReputationProfileMatch;
import dev.otectus.mcaquests.compat.ReputationProfileQuery;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.leaf.ProfileCondition;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import dev.otectus.mcaquests.quest.reputation.ReputationDedupe;
import dev.otectus.mcaquests.quest.reputation.ReputationOutcome;
import dev.otectus.mcaquests.quest.reward.RecordIncidentReward;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCA: Reputation 0.6.0 adoption: typed delivery, the delta the author actually wrote, speaker-aware
 * selectors, capability gating, and the {@code mcareputation:profile} condition.
 *
 * <p>Everything here is pure. The canonical backend needs a live server and a village, so the parts
 * that only it can answer are asserted two ways: the plain translation types it produces are exercised
 * directly, and the call shapes inside it are asserted against its source — the same technique the
 * classload tripwires in {@link ReputationIntegrationTest} use, and for the same reason. A behaviour
 * that can only be checked by starting a world is named as such rather than faked here.
 */
class ReputationProfileAdoptionTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation QUEST = new ResourceLocation("example", "commission");
    private static final ResourceLocation RELIABILITY =
            new ResourceLocation("mcareputation", "reliability");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @AfterEach
    void reset() {
        ReputationBridge.resetForTest();
    }

    // ------------------------------------------------------------------
    // An omitted delta is not an explicit zero (§16.1, audit R15)
    // ------------------------------------------------------------------

    private static ReputationAward.Builder award() {
        return ReputationAward.builder(null, ALICE, OVERWORLD, 3,
                new ResourceLocation("mcaquests", "quests"));
    }

    @Test
    @DisplayName("an award that prices nothing carries no delta at all")
    void omittedDeltaStaysOmitted() {
        ReputationAward unpriced = award().incident(new ResourceLocation("mcareputation:promise_kept"))
                .build();
        assertTrue(unpriced.delta().isEmpty(),
                "an unpriced deed must reach Reputation with no override, so the incident "
                        + "definition's own default_delta applies");
        assertEquals(0, unpriced.deltaOrZero(), "the legacy store still needs a number");
        assertFalse(unpriced.isNoOp(), "it names a deed, so there is something to record");
    }

    @Test
    @DisplayName("an explicit zero is an instruction and survives as one")
    void explicitZeroIsKept() {
        ReputationAward priced = award().incident(new ResourceLocation("mcareputation:promise_kept"))
                .delta(0).build();
        assertEquals(OptionalInt.of(0), priced.delta(),
                "\"record this deed and move no standing\" is a real request, and the only way to "
                        + "carry profile evidence without a number");
    }

    @Test
    @DisplayName("nothing priced and nothing named is a no-op")
    void nothingAtAllIsANoOp() {
        assertTrue(award().build().isNoOp());
        assertTrue(award().delta(0).build().isNoOp(),
                "a zero with no deed named still says nothing to anybody");
        assertFalse(award().delta(3).build().isNoOp());
    }

    @Test
    @DisplayName("the Optional overload keeps an author's silence silent")
    void optionalDeltaOverload() {
        assertTrue(award().delta(Optional.<Integer>empty()).build().delta().isEmpty());
        assertEquals(OptionalInt.of(-4), award().delta(Optional.of(-4)).build().delta());
    }

    /** The defect in the reward itself: {@code delta.orElse(0)} priced every unpriced deed at zero. */
    @Test
    @DisplayName("record_incident passes an omitted delta through as omitted")
    void recordIncidentRewardKeepsOmittedDelta() {
        RecordIncidentReward reward = RecordIncidentReward.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"incident": "mcareputation:restitution_completed"}"""))
                .result().orElseThrow();
        assertTrue(reward.delta().isEmpty());
        assertTrue(reward.incidentProfile().isEmpty());

        RecordIncidentReward explicit = RecordIncidentReward.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"incident": "mcareputation:restitution_completed", "delta": 0,
                         "incident_profile": "mcaquests:quest_commission"}"""))
                .result().orElseThrow();
        assertEquals(Optional.of(0), explicit.delta());
        assertEquals("mcaquests:quest_commission",
                explicit.incidentProfile().orElseThrow().toString());
    }

    // ------------------------------------------------------------------
    // The authored social profile travels with the outcome
    // ------------------------------------------------------------------

    @Test
    @DisplayName("incident_profile parses and survives both defaulting helpers")
    void outcomeCarriesTheProfile() {
        ReputationOutcome outcome = ReputationOutcome.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"delta": 8, "incident_profile": "mcaquests:quest_commission",
                         "recipients": "resolving_player"}"""))
                .result().orElseThrow();
        assertEquals("mcaquests:quest_commission", outcome.incidentProfile().orElseThrow().toString());

        ReputationOutcome defaulted = outcome
                .withDefaultIncident(new ResourceLocation("mcareputation", "quest_completed"))
                .withDefaultRecipients(ReputationOutcome.Recipients.ALL_PARTICIPANTS);
        assertEquals("mcaquests:quest_commission", defaulted.incidentProfile().orElseThrow().toString(),
                "filling in a default incident must not drop the authored profile");
    }

    @Test
    @DisplayName("the legacy integer shorthand still names no profile")
    void shorthandNamesNoProfile() {
        assertTrue(ReputationOutcome.ofShorthand(10).incidentProfile().isEmpty());
    }

    /** Both shipped profiles must admit exactly the Quests deeds they were written for. */
    @Test
    @DisplayName("the shipped Quests profiles allow the incidents Quests records")
    void shippedProfilesAllowTheirIncidents() throws IOException {
        Path root = Path.of("src/main/resources/data/mcaquests/mcareputation/incident_profiles");
        String commission = Files.readString(root.resolve("quest_commission.json"),
                StandardCharsets.UTF_8);
        assertTrue(commission.contains("\"mcareputation:quest_completed\""),
                "the commission profile exists because Reputation's kept_commitment allows only "
                        + "promise_kept, so a completed quest had no profile to be evidence for");
        assertTrue(commission.contains("\"mcareputation:reliability\""));
        assertTrue(commission.contains("\"credit_class\": \"commendable\""));

        String broken = Files.readString(root.resolve("quest_commitment_broken.json"),
                StandardCharsets.UTF_8);
        assertTrue(broken.contains("\"mcareputation:quest_failed\"")
                && broken.contains("\"mcareputation:quest_abandoned\""));
        assertTrue(broken.contains("\"credit_class\": \"adverse\""),
                "repetition must never make a broken commitment cheaper");
    }

    // ------------------------------------------------------------------
    // Typed delivery outcomes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("accepted covers applied, duplicate and accepted-but-private")
    void acceptedOutcomes() {
        assertTrue(ReputationDeliveryResult.Status.APPLIED.accepted());
        assertTrue(ReputationDeliveryResult.Status.DUPLICATE.accepted(),
                "the deed is on the record; re-paying it is the bug this distinction prevents");
        assertTrue(ReputationDeliveryResult.Status.ACCEPTED_NO_PUBLIC_INCIDENT.accepted());
        assertFalse(ReputationDeliveryResult.Status.REFUSED_CAPACITY.accepted());
        assertFalse(ReputationDeliveryResult.Status.REFUSED_INVALID.accepted());
        assertFalse(ReputationDeliveryResult.Status.UNAVAILABLE.accepted());
    }

    @Test
    @DisplayName("a backend with no ledger says so instead of claiming success")
    void unavailableIsNotSuccess() {
        ReputationDeliveryResult result = ReputationDeliveryResult.unavailable();
        assertEquals(ReputationDeliveryResult.Status.UNAVAILABLE, result.status());
        assertFalse(result.applied());
        assertFalse(result.retryable(), "there is nothing to retry into without a ledger");
        assertTrue(result.appliedProfile().isEmpty());
    }

    /** The interface default keeps every pre-1.6.6 backend, add-ons included, behaving as before. */
    @Test
    @DisplayName("the default deliver() reports what award() did")
    void defaultDeliveryFollowsAward() {
        RecordingBackend backend = new RecordingBackend();
        ReputationDeliveryResult result = backend.deliver(award().delta(7).build());
        assertEquals(ReputationDeliveryResult.Status.APPLIED, result.status());
        assertEquals(41, result.newScore());
    }

    @Test
    @DisplayName("a no-op award never reaches the backend")
    void noOpAwardShortCircuits() {
        RecordingBackend backend = new RecordingBackend();
        ReputationBridge.setBackendForTest(backend);
        assertEquals(ReputationDeliveryResult.Status.UNAVAILABLE,
                QuestReputation.deliver(award().build()).status());
        assertEquals(0, backend.awards, "nothing was asked for, so nothing was delivered");
    }

    // ------------------------------------------------------------------
    // Capability gating (§14.4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the fallback backend advertises no feature at all")
    void legacyBackendAdvertisesNothing() {
        ReputationBridge.resetForTest();
        for (String feature : List.of(ReputationFeatures.DELIVERY, ReputationFeatures.SPEAKER_QUERY,
                ReputationFeatures.BOUND_RESOLUTION, ReputationFeatures.LADDER_HIGH_WATER,
                ReputationFeatures.PROFILE_SNAPSHOT, ReputationFeatures.SPEAKER_PROFILE,
                ReputationFeatures.PROFILED_DELIVERY, ReputationFeatures.PROFILE_CHANGE,
                ReputationFeatures.REPEAT_CREDIT)) {
            assertFalse(ReputationBridge.backend().supportsFeature(null, feature),
                    feature + " must not be advertised by a store that cannot do it");
        }
    }

    @Test
    @DisplayName("feature ids are exactly the strings MCA: Reputation publishes")
    void featureIdsMatchTheProtocol() {
        assertEquals("profile_snapshot_v1", ReputationFeatures.PROFILE_SNAPSHOT);
        assertEquals("speaker_profile_v1", ReputationFeatures.SPEAKER_PROFILE);
        assertEquals("repeat_credit_v1", ReputationFeatures.REPEAT_CREDIT);
        assertEquals("profiled_delivery_v1", ReputationFeatures.PROFILED_DELIVERY);
        assertEquals("profile_change_v1", ReputationFeatures.PROFILE_CHANGE);
        assertEquals("delivery", ReputationFeatures.DELIVERY);
        assertEquals("speaker_query", ReputationFeatures.SPEAKER_QUERY);
        assertEquals("bound_resolution", ReputationFeatures.BOUND_RESOLUTION);
        assertEquals("ladder_high_water", ReputationFeatures.LADDER_HIGH_WATER);
    }

    @Test
    @DisplayName("a capability probe that throws answers 'absent', not an exception")
    void capabilityProbeIsContained() {
        ReputationBridge.setBackendForTest(new ThrowingBackend());
        assertFalse(QuestReputation.supportsFeature(null, ReputationFeatures.PROFILE_SNAPSHOT));
    }

    @Test
    @DisplayName("a gated feature is asked for by name, once per question")
    void gatedFeaturesAreAsked() {
        RecordingBackend backend = new RecordingBackend();
        ReputationBridge.setBackendForTest(backend);
        assertTrue(QuestReputation.supportsFeature(null, ReputationFeatures.DELIVERY));
        assertFalse(QuestReputation.supportsFeature(null, ReputationFeatures.PROFILE_SNAPSHOT));
        assertEquals(List.of(ReputationFeatures.DELIVERY, ReputationFeatures.PROFILE_SNAPSHOT),
                backend.featuresAsked);
    }

    // ------------------------------------------------------------------
    // The speaker-aware selector path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the giver is carried into the incident question")
    void giverReachesTheBackend() {
        RecordingBackend backend = new RecordingBackend();
        ReputationBridge.setBackendForTest(backend);
        QuestReputation.Community community = new QuestReputation.Community(OVERWORLD, 3);
        IncidentSelector selector = new IncidentSelector(
                List.of(new ResourceLocation("mcareputation", "villager_assaulted")),
                List.of("active"), List.of(), true, 0L);

        QuestReputation.hasIncident(null, ALICE, community, selector);
        assertEquals(1, backend.incidentQuestions,
                "the five-argument form still works and still reaches the backend");

        QuestReputation.resolveIncident(null, ALICE, community, selector, "atoned",
                ReputationDedupe.incidentResolution(QUEST, ALICE, "atoned"));
        assertEquals("incident:example:commission:" + ALICE + ":atoned", backend.lastDedupeKey,
                "the dedupe key must reach the backend; ignoring it is what let a retry resolve a "
                        + "different deed than the reward was granted for");
    }

    @Test
    @DisplayName("one quest copy's resolution is one operation, and the next copy's is another")
    void resolutionKeysArePerQuestCopy() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(ReputationDedupe.incidentResolution(QUEST, first, "atoned"),
                ReputationDedupe.incidentResolution(QUEST, first, "atoned"),
                "a replay of the same acceptance is the same operation");
        List<String> distinct = List.of(
                ReputationDedupe.incidentResolution(QUEST, first, "atoned"),
                ReputationDedupe.incidentResolution(QUEST, second, "atoned"),
                ReputationDedupe.incidentResolution(QUEST, first, "forgiven"),
                ReputationDedupe.incidentResolution(new ResourceLocation("example:other"), first,
                        "atoned"));
        assertEquals(distinct.size(), Set.copyOf(distinct).size(),
                "a repeatable restitution quest accepted again may atone for a second incident");
        assertNotEquals(ReputationDedupe.incidentResolution(QUEST, null, "atoned"),
                ReputationDedupe.incidentResolution(QUEST, first, "atoned"));
    }

    // ------------------------------------------------------------------
    // Profile predicates and their three-valued answer
    // ------------------------------------------------------------------

    @Test
    @DisplayName("'cannot say' is never silently 'no'")
    void unavailableIsNotFalse() {
        ReputationProfileMatch unsupported = ReputationProfileMatch.unsupported();
        assertFalse(unsupported.isAvailable());
        assertFalse(unsupported.matched());
        assertTrue(unsupported.orElse(true), "the authored fallback decides, not the absence");
        assertFalse(ReputationProfileMatch.available(false).orElse(true),
                "a real 'no' is never overridden by the fallback");
    }

    @Test
    @DisplayName("an availability value can never claim to be available without a verdict")
    void unavailableNormalises() {
        ReputationProfileMatch coerced = ReputationProfileMatch.unavailable(
                ReputationProfileMatch.Availability.AVAILABLE, "nonsense");
        assertEquals(ReputationProfileMatch.Availability.UNRESOLVED, coerced.availability());
    }

    @Test
    @DisplayName("a backend that throws answers ERROR, so the fallback runs")
    void profileErrorsAreContained() {
        ReputationBridge.setBackendForTest(new ThrowingBackend());
        ReputationProfileMatch match = QuestReputation.matchesProfile(null, ALICE,
                new QuestReputation.Community(OVERWORLD, 3), null,
                ReputationProfileQuery.community().with(
                        ReputationProfileQuery.Facet.atLeast(RELIABILITY, 10)));
        assertEquals(ReputationProfileMatch.Availability.ERROR, match.availability());
    }

    @Test
    @DisplayName("the fallback backend cannot answer a profile question")
    void legacyBackendHasNoProfiles() {
        ReputationBridge.resetForTest();
        ReputationProfileMatch match = QuestReputation.matchesProfile(null, ALICE,
                new QuestReputation.Community(OVERWORLD, 3), null,
                ReputationProfileQuery.community().with(
                        ReputationProfileQuery.Facet.atLeast(RELIABILITY, 10)));
        assertEquals(ReputationProfileMatch.Availability.UNSUPPORTED, match.availability());
    }

    @Test
    @DisplayName("an invalid query is refused before it is asked")
    void invalidQueriesAreRejected() {
        assertFalse(new ReputationProfileQuery(ReputationProfileQuery.Scope.COMMUNITY,
                OptionalInt.of(40), OptionalInt.of(10), Optional.empty(), List.of(), false).valid(),
                "inverted recognition bounds");
        assertFalse(ReputationProfileQuery.community()
                .with(ReputationProfileQuery.Facet.atLeast(RELIABILITY, 10))
                .with(ReputationProfileQuery.Facet.atLeast(RELIABILITY, 20)).valid(),
                "one clause per facet; two is a contradiction waiting to be resolved arbitrarily");
        assertFalse(new ReputationProfileQuery(ReputationProfileQuery.Scope.COMMUNITY,
                OptionalInt.empty(), OptionalInt.empty(), Optional.of("  "), List.of(), false).valid(),
                "a blank tier id");
        assertFalse(new ReputationProfileQuery(ReputationProfileQuery.Scope.COMMUNITY,
                OptionalInt.empty(), OptionalInt.empty(), Optional.empty(),
                new ArrayList<>(List.of(new ReputationProfileQuery.Facet(RELIABILITY,
                        OptionalInt.of(30), OptionalInt.of(5), 1, false))), false).valid(),
                "inverted facet bounds");

        ReputationProfileQuery sane = ReputationProfileQuery.community()
                .with(ReputationProfileQuery.Facet.atLeast(RELIABILITY, 10));
        assertTrue(sane.valid());
        assertFalse(sane.isEmpty());
        assertFalse(sane.dependsOnCompleteHistory(),
                "an observed lower bound answers on a partial history: missing evidence can only hide "
                        + "more evidence");
    }

    @Test
    @DisplayName("absence-of-evidence clauses depend on a complete history")
    void absenceClausesNeedCompleteHistory() {
        assertTrue(ReputationProfileQuery.community()
                .with(new ReputationProfileQuery.Facet(new ResourceLocation("mcareputation:violence"),
                        OptionalInt.empty(), OptionalInt.of(0), 1, false))
                .dependsOnCompleteHistory(), "an upper bound is a claim about what is not there");
        assertTrue(ReputationProfileQuery.community()
                .with(new ReputationProfileQuery.Facet(new ResourceLocation("mcareputation:violence"),
                        OptionalInt.empty(), OptionalInt.empty(), 0, true))
                .dependsOnCompleteHistory(), "allow_unobserved is the named escape hatch, and it is one");
    }

    // ------------------------------------------------------------------
    // The mcareputation:profile condition's JSON
    // ------------------------------------------------------------------

    private static ProfileCondition parse(String json) {
        return ProfileCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElseThrow(() -> new AssertionError("expected a parse: " + json));
    }

    @Test
    @DisplayName("the documented shape parses into the query it describes")
    void documentedShapeParses() {
        ProfileCondition condition = parse("""
                {
                  "scope": "giver",
                  "recognition": { "min": 15 },
                  "facets": {
                    "mcareputation:reliability": { "min": 20, "min_evidence": 2 }
                  },
                  "allow_partial_history": false
                }""");
        ReputationProfileQuery query = condition.query();
        assertEquals(ReputationProfileQuery.Scope.GIVER, query.scope());
        assertEquals(OptionalInt.of(15), query.minRecognition());
        assertEquals(1, query.facets().size());
        ReputationProfileQuery.Facet facet = query.facets().get(0);
        assertEquals(RELIABILITY, facet.facet());
        assertEquals(OptionalInt.of(20), facet.min());
        assertEquals(2, facet.minEvidence());
        assertFalse(facet.allowUnobserved());
        assertFalse(query.allowPartialHistory());
        assertFalse(condition.allowWhenUnavailable(), "deny is the default");
        assertTrue(query.valid());
    }

    @Test
    @DisplayName("scope defaults to the village, and min_evidence to one")
    void defaults() {
        ProfileCondition condition = parse("""
                {"recognition": {"min": 1}, "facets": {"mcareputation:reliability": {"min": 5}}}""");
        assertEquals(ReputationProfileQuery.Scope.COMMUNITY, condition.query().scope());
        assertEquals(1, condition.query().facets().get(0).minEvidence(),
                "a facet clause requires live evidence unless the author says otherwise; an "
                        + "unobserved facet is not evidence of the opposite");
    }

    @Test
    @DisplayName("'does this villager know me' is scope giver with a recognition floor")
    void knownToThisVillager() {
        ProfileCondition condition = parse("""
                {"scope": "giver", "recognition": {"min": 1}}""");
        assertEquals(ReputationProfileQuery.Scope.GIVER, condition.query().scope());
        assertEquals(OptionalInt.of(1), condition.query().minRecognition());
        assertTrue(condition.query().facets().isEmpty());
    }

    @Test
    @DisplayName("on_unavailable is authored, and only in the two spellings that mean something")
    void onUnavailable() {
        assertTrue(parse("""
                {"recognition": {"min": 5}, "on_unavailable": "allow"}""").allowWhenUnavailable());
        assertFalse(parse("""
                {"recognition": {"min": 5}, "on_unavailable": "deny"}""").allowWhenUnavailable());
        assertTrue(ProfileCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"recognition": {"min": 5}, "on_unavailable": "maybe"}""")).error().isPresent(),
                "a misspelling must be a load error, not a silent deny");
    }

    @Test
    @DisplayName("an unknown scope is a load error")
    void unknownScopeIsAnError() {
        assertTrue(ProfileCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"scope": "village_elder", "recognition": {"min": 5}}""")).error().isPresent());
    }

    @Test
    @DisplayName("a malformed bound is reported rather than defaulted away")
    void strictFields() {
        assertTrue(ProfileCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"recognition": {"min": "quite well known"}}""")).error().isPresent(),
                "StrictCodecs exists so a malformed gate is never read as 'no requirement'");
    }

    /**
     * A block with no requirement in it parses — nothing in the JSON is wrong — and is then refused at
     * evaluation rather than passing everything. Asserting the query's own emptiness is the checkable
     * half of that; the refusal itself needs a giver and a village.
     */
    @Test
    @DisplayName("a profile block that asks nothing is recognisably empty")
    void emptyQueryIsRecognised() {
        assertTrue(parse("{}").query().isEmpty());
        assertTrue(parse("{\"facets\": {}}").query().isEmpty());
        assertFalse(parse("{\"min_recognition_tier\": \"recognized\"}").query().isEmpty());
    }

    @Test
    @DisplayName("every facet clause is asked in a stable order")
    void facetsAreSorted() {
        ProfileCondition condition = parse("""
                {"facets": {"mcareputation:violence": {"max": 0, "allow_unobserved": true},
                            "mcareputation:bravery": {"min": 10},
                            "mcareputation:reliability": {"min": 5}}}""");
        assertEquals(List.of("mcareputation:bravery", "mcareputation:reliability",
                        "mcareputation:violence"),
                condition.query().facets().stream().map(f -> f.facet().toString()).toList());
    }

    @Test
    @DisplayName("the condition is registered, and reachable through the datapack dispatcher")
    void registeredAndDispatched() {
        assertEquals("mcareputation:profile", ConditionTypes.PROFILE.id().toString());
        assertTrue(ConditionTypes.exists(new ResourceLocation("mcareputation", "profile")));
        QuestCondition parsed = ConditionTypes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"type": "mcareputation:profile", "scope": "giver", "recognition": {"min": 5}}"""))
                .result().orElseThrow(() -> new AssertionError("the registry does not dispatch it"));
        assertTrue(parsed instanceof ProfileCondition);
        assertEquals(ConditionTypes.PROFILE, parsed.type());
    }

    // ------------------------------------------------------------------
    // Call shapes inside the guarded backend (§29.1: it cannot be loaded here)
    // ------------------------------------------------------------------

    private static final Path BACKEND = Path.of(
            "src/main/java/dev/otectus/mcaquests/compat/reputation/CanonicalReputationBackend.java");

    @Test
    @DisplayName("the backend delivers, binds, carries the ladder and asks about the speaker")
    void backendUsesTheAdoptedApi() throws IOException {
        String source = Files.readString(BACKEND, StandardCharsets.UTF_8);
        Map<String, String> required = Map.of(
                "McaReputationApi.deliver(", "an award must be delivered, not merely recorded",
                "McaReputationApi.deliverProfiled(", "an authored profile must reach Reputation",
                "McaReputationApi.resolveBound(", "a settled resolution must bind an exact incident",
                "McaReputationApi.highWaterTierId(", "the requested ladder must be honoured",
                "McaReputationApi.speakerContext(", "a known_to_giver selector needs a real speaker",
                "McaReputationApi.matchesSpeakerProfile(", "a giver-scoped profile query",
                "McaReputationApi.capabilities(", "features are negotiated, not reflected upon");
        List<String> missing = new ArrayList<>();
        required.forEach((call, why) -> {
            if (!source.contains(call)) {
                missing.add(call + " — " + why);
            }
        });
        assertEquals(List.of(), missing, "the canonical backend no longer uses the 0.6.0 surface it "
                + "was adopted against");
    }

    @Test
    @DisplayName("the reflective method probes are gone")
    void noReflectiveProbes() throws IOException {
        String source = Files.readString(BACKEND, StandardCharsets.UTF_8);
        assertFalse(source.contains(".getMethod("),
                "capability strings replaced the getMethod probes: a method existing says nothing "
                        + "about whether the feature behind it can answer now");
    }

    @Test
    @DisplayName("a giver-scoped query never falls back to the village")
    void speakerScopeNeverWidens() throws IOException {
        String source = Files.readString(BACKEND, StandardCharsets.UTF_8);
        assertTrue(source.contains("no_speaker_context"),
                "a missing giver must answer 'cannot say' with that reason, never the community "
                        + "profile (§13.3)");
    }

    // ------------------------------------------------------------------
    // Stubs
    // ------------------------------------------------------------------

    /** A backend that records what it was asked, and claims exactly the 0.5.0 delivery feature. */
    private static final class RecordingBackend implements ReputationBackend {

        private final List<String> featuresAsked = new ArrayList<>();
        private int awards;
        private int incidentQuestions;
        @Nullable private String lastDedupeKey = "unset";

        @Override
        public boolean isCanonical() {
            return true;
        }

        @Override
        public String backendName() {
            return "test:recording";
        }

        @Override
        public boolean supportsFeature(MinecraftServer server, String feature) {
            featuresAsked.add(feature);
            return ReputationFeatures.DELIVERY.equals(feature);
        }

        @Override
        public int score(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId) {
            return 34;
        }

        @Override
        public String tierId(MinecraftServer server, UUID player, ResourceLocation dimension,
                             int villageId, ResourceLocation ladder) {
            return "stranger";
        }

        @Override
        public int tierIndex(MinecraftServer server, UUID player, ResourceLocation dimension,
                             int villageId, ResourceLocation ladder) {
            return 0;
        }

        @Override
        public Map<Integer, Integer> villageScores(MinecraftServer server, UUID player,
                                                   ResourceLocation dimension) {
            return Map.of();
        }

        @Override
        public Optional<String> tierHighWater(MinecraftServer server, UUID player,
                                              ResourceLocation dimension, int villageId,
                                              ResourceLocation ladder) {
            return Optional.of(ladder.toString());
        }

        @Override
        public int award(ReputationAward award) {
            awards++;
            return 34 + award.deltaOrZero();
        }

        @Override
        public boolean grantTitle(MinecraftServer server, UUID player, ResourceLocation dimension,
                                  int villageId, ResourceLocation title, boolean global) {
            return false;
        }

        @Override
        public boolean hasTitle(MinecraftServer server, UUID player, ResourceLocation dimension,
                                int villageId, ResourceLocation title, boolean global) {
            return false;
        }

        @Override
        public Set<ResourceLocation> globalTitles(MinecraftServer server, UUID player) {
            return Set.of();
        }

        @Override
        public Set<ResourceLocation> villageTitles(MinecraftServer server, UUID player,
                                                   ResourceLocation dimension, int villageId) {
            return Set.of();
        }

        @Override
        public boolean hasIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                   int villageId, IncidentSelector selector) {
            incidentQuestions++;
            return false;
        }

        @Override
        public boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                       int villageId, IncidentSelector selector, String resolution,
                                       @Nullable String dedupeKey) {
            lastDedupeKey = dedupeKey;
            return false;
        }

        @Override
        public boolean recordIncident(ReputationAward award) {
            return false;
        }
    }

    /** Every optional path throws, to prove each one is contained rather than propagated. */
    private static final class ThrowingBackend implements ReputationBackend {

        @Override
        public boolean isCanonical() {
            return true;
        }

        @Override
        public String backendName() {
            return "test:throwing";
        }

        @Override
        public boolean supportsFeature(MinecraftServer server, String feature) {
            throw new IllegalStateException("capability probe exploded");
        }

        @Override
        public int score(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId) {
            throw new IllegalStateException("no");
        }

        @Override
        public String tierId(MinecraftServer server, UUID player, ResourceLocation dimension,
                             int villageId, ResourceLocation ladder) {
            throw new IllegalStateException("no");
        }

        @Override
        public int tierIndex(MinecraftServer server, UUID player, ResourceLocation dimension,
                             int villageId, ResourceLocation ladder) {
            throw new IllegalStateException("no");
        }

        @Override
        public Map<Integer, Integer> villageScores(MinecraftServer server, UUID player,
                                                   ResourceLocation dimension) {
            throw new IllegalStateException("no");
        }

        @Override
        public Optional<String> tierHighWater(MinecraftServer server, UUID player,
                                              ResourceLocation dimension, int villageId,
                                              ResourceLocation ladder) {
            throw new IllegalStateException("no");
        }

        @Override
        public int award(ReputationAward award) {
            throw new IllegalStateException("no");
        }

        @Override
        public boolean grantTitle(MinecraftServer server, UUID player, ResourceLocation dimension,
                                  int villageId, ResourceLocation title, boolean global) {
            throw new IllegalStateException("no");
        }

        @Override
        public boolean hasTitle(MinecraftServer server, UUID player, ResourceLocation dimension,
                                int villageId, ResourceLocation title, boolean global) {
            throw new IllegalStateException("no");
        }

        @Override
        public Set<ResourceLocation> globalTitles(MinecraftServer server, UUID player) {
            throw new IllegalStateException("no");
        }

        @Override
        public Set<ResourceLocation> villageTitles(MinecraftServer server, UUID player,
                                                   ResourceLocation dimension, int villageId) {
            throw new IllegalStateException("no");
        }

        @Override
        public boolean hasIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                   int villageId, IncidentSelector selector) {
            throw new IllegalStateException("no");
        }

        @Override
        public boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                       int villageId, IncidentSelector selector, String resolution,
                                       @Nullable String dedupeKey) {
            throw new IllegalStateException("no");
        }

        @Override
        public boolean recordIncident(ReputationAward award) {
            throw new IllegalStateException("no");
        }

        @Override
        public ReputationProfileMatch matchesProfile(MinecraftServer server, UUID player,
                                                     ResourceLocation dimension, int villageId,
                                                     @Nullable Entity giver,
                                                     ReputationProfileQuery query) {
            throw new IllegalStateException("profile query exploded");
        }
    }
}
