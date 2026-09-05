package dev.otectus.mcaquests.compat.reputation;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.compat.ReputationAward;
import dev.otectus.mcaquests.compat.ReputationBackend;
import dev.otectus.mcaquests.compat.ReputationBridge;
import dev.otectus.mcaquests.compat.VillagerOpinionView;
import dev.otectus.mcaquests.state.QuestCapabilities;
import dev.otectus.mcareputation.api.IncidentQuery;
import dev.otectus.mcareputation.api.McaReputationApi;
import dev.otectus.mcareputation.api.ReputationRequest;
import dev.otectus.mcareputation.api.ReputationResult;
import dev.otectus.mcareputation.api.ResolutionResult;
import dev.otectus.mcareputation.community.CommunityKey;
import dev.otectus.mcareputation.incident.IncidentStatus;
import dev.otectus.mcareputation.incident.IncidentSubject;
import dev.otectus.mcareputation.incident.IncidentVisibility;
import dev.otectus.mcareputation.reputation.ReputationTierSet;
import dev.otectus.mcareputation.reputation.ReputationTiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    @Override
    public Optional<String> tierHighWater(MinecraftServer server, UUID player, ResourceLocation dimension,
                                          int villageId, ResourceLocation ladder) {
        return key(dimension, villageId)
                .flatMap(community -> McaReputationApi.getSnapshot(server, player, community))
                .flatMap(snapshot -> snapshot.highWaterTierId());
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    @Override
    public int award(ReputationAward award) {
        Optional<CommunityKey> community = key(award.dimension(), award.villageId());
        if (community.isEmpty()) {
            return 0;
        }
        ResourceLocation incidentType = award.incidentType() != null
                ? award.incidentType()
                // A reward with no authored incident type still deserves a named story rather than an
                // anonymous number, so it lands as the generic quest completion (§16).
                : ResourceLocation.fromNamespaceAndPath("mcareputation", "quest_completed");

        ReputationRequest.Builder request = ReputationRequest
                .builder(award.server(), award.player(), community.get(), incidentType, award.source())
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

        ReputationResult result = McaReputationApi.record(request.build());
        if (!result.applied() && result.reason() != ReputationResult.Reason.DUPLICATE) {
            McaQuests.LOGGER.debug("[MCA: Quests] reputation award for {} was not applied ({})",
                    award.player(), result.reason());
        }
        return result.newScore();
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
        return key(dimension, villageId)
                .map(community -> !McaReputationApi
                        .selectIncidents(server, player, community, toQuery(selector, false)).isEmpty())
                .orElse(false);
    }

    @Override
    public boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                   int villageId, IncidentSelector selector, String resolution,
                                   @Nullable String dedupeKey) {
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
        return key(dimension, villageId).map(community -> {
            ResolutionResult result = McaReputationApi.resolveBySelector(server, player, community,
                    toQuery(selector, true), status.get(),
                    ResourceLocation.fromNamespaceAndPath("mcaquests", "quests"));
            return result.applied();
        }).orElse(false);
    }

    @Override
    public boolean recordIncident(ReputationAward award) {
        return award(award) != 0 || award.delta() == 0;
    }

    // ------------------------------------------------------------------
    // Per-villager opinion
    // ------------------------------------------------------------------

    /**
     * {@code McaReputationApi.getVillagerOpinion}, resolved once and remembered, or null when this
     * MCA: Reputation has no such method.
     *
     * <p>PORT: the 1.21.1 MCA: Reputation sibling this class compiles against does not have the
     * opinion API yet — neither the method nor the opinion and basis types it returns — so nothing
     * here can be a static call. Everything is looked up by name instead, which makes the path inert
     * (always {@link Optional#empty()}) today and makes it light up the moment a Reputation build
     * carrying {@code getVillagerOpinion} is installed, with no Quests rebuild.
     */
    private static volatile Method opinionMethod;

    /** Whether the probe above has run; separate from the result, which is legitimately null. */
    private static volatile boolean opinionProbed;

    /** The {@code opinion}/{@code tierId}/{@code basis} accessors of one opinion class, in that order. */
    private static final Map<Class<?>, Method[]> OPINION_ACCESSORS = new ConcurrentHashMap<>();

    @Nullable
    private static Method opinionMethod() {
        if (opinionProbed) {
            return opinionMethod;
        }
        Method found = null;
        try {
            found = McaReputationApi.class.getMethod("getVillagerOpinion", MinecraftServer.class,
                    UUID.class, UUID.class, CommunityKey.class);
        } catch (NoSuchMethodException e) {
            // Debug, and only once: a Reputation without the opinion API is a supported installation.
            McaQuests.LOGGER.debug("[MCA: Quests] this MCA: Reputation has no per-villager opinion API; "
                    + "opinion conditions will not be met");
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] could not probe the per-villager opinion API", t);
        }
        opinionMethod = found;
        opinionProbed = true;
        return found;
    }

    /**
     * Reads one opinion object through its own class, so the record's type never has to be named here.
     *
     * <p>Any failure — a missing accessor, a changed return type, an exception from the API itself —
     * is one debug line and no opinion, which the condition reads as "not met" (§35.1).
     */
    @Override
    public Optional<VillagerOpinionView> villagerOpinion(MinecraftServer server, UUID player, UUID villager,
                                                         ResourceLocation dimension, int villageId) {
        Method method = opinionMethod();
        if (method == null) {
            return Optional.empty();
        }
        Optional<CommunityKey> community = key(dimension, villageId);
        if (community.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object result = method.invoke(null, server, player, villager, community.get());
            if (result instanceof Optional<?> wrapped) {
                result = wrapped.orElse(null);
            }
            if (result == null) {
                return Optional.empty();
            }
            Method[] accessors = OPINION_ACCESSORS.computeIfAbsent(result.getClass(), type -> {
                try {
                    return new Method[] {type.getMethod("opinion"), type.getMethod("tierId"),
                            type.getMethod("basis")};
                } catch (Throwable t) {
                    throw new IllegalStateException("unreadable opinion type " + type.getName(), t);
                }
            });
            int opinion = ((Number) accessors[0].invoke(result)).intValue();
            String tierId = String.valueOf(accessors[1].invoke(result));
            return Optional.of(new VillagerOpinionView(opinion, tierId, basisName(accessors[2].invoke(result))));
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] could not read this villager's opinion; "
                    + "the condition is not met", t);
            return Optional.empty();
        }
    }

    /** The basis enum's own json name where it has one, and its constant name lower-cased otherwise. */
    private static String basisName(@Nullable Object basis) throws Exception {
        if (basis == null) {
            return "none";
        }
        try {
            return String.valueOf(basis.getClass().getMethod("jsonName").invoke(basis));
        } catch (NoSuchMethodException e) {
            return basis.toString().toLowerCase(Locale.ROOT);
        }
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
