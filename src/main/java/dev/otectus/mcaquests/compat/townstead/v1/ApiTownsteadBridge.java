package dev.otectus.mcaquests.compat.townstead.v1;

import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.model.BuildingSnapshot;
import com.aetherianartificer.townstead.api.v1.model.CalendarSnapshot;
import com.aetherianartificer.townstead.api.v1.model.GeneSnapshot;
import com.aetherianartificer.townstead.api.v1.model.NeedLevel;
import com.aetherianartificer.townstead.api.v1.model.NeedsSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProgressionTrackSnapshot;
import com.aetherianartificer.townstead.api.v1.model.RootSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ScheduleSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SpiritSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillagerRecord;
import com.aetherianartificer.townstead.api.v1.model.VillagerSnapshot;
import com.aetherianartificer.townstead.api.v1.result.NeedResult;
import com.aetherianartificer.townstead.api.v1.result.SkillResult;
import com.aetherianartificer.townstead.api.v1.result.XpResult;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.NeedMutation;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadBuildingView;
import dev.otectus.mcaquests.compat.TownsteadCalendarView;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadCounters;
import dev.otectus.mcaquests.compat.TownsteadGeneVariantView;
import dev.otectus.mcaquests.compat.TownsteadGeneView;
import dev.otectus.mcaquests.compat.TownsteadLifeStageView;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadProfessionTrackView;
import dev.otectus.mcaquests.compat.TownsteadResidentRecordView;
import dev.otectus.mcaquests.compat.TownsteadRootView;
import dev.otectus.mcaquests.compat.TownsteadScheduleView;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.compat.TownsteadStatus;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The typed Townstead bridge, for Townstead builds that ship {@code api.v1}. Every method is a
 * direct call into a frozen, versioned surface that names no MCA type, so nothing here can be
 * linked to an MCA package layout. Chosen by {@code TownsteadCompat} when the API class is
 * present; older Townstead builds keep the reflective bridge, and a Townstead that ships the API
 * is never bound any other way.
 *
 * <p>Only ever loaded after the mod-present check, which is why it may import Townstead types
 * freely. {@code NoTownsteadStaticLinkTest} exempts this package for the same reason it exempts
 * the Reputation adapter, and asserts that it names nothing of Townstead's outside {@code api/v1}.
 *
 * <h2>Capabilities</h2>
 *
 * <p>Every {@link TownsteadCapability} maps to a method that is part of the v1 contract
 * ({@code TownsteadApiV1} promises methods are never removed), so all of them bind when the
 * installed API is generation 1. What the contract does <em>not</em> promise is that a write will
 * be accepted: a server can refuse writes from this mod's source id in Townstead's config, and
 * Townstead gates thirst and temperature behind other mods. Those come back per call as
 * {@link TownsteadMutationResult.Reason#FEATURE_GATED}, never as a missing capability, because
 * they are policy rather than breakage and can change without a restart.
 *
 * <h2>Result semantics</h2>
 *
 * <p>Results carry the same numbers the reflective bridge reports, so nothing downstream can tell
 * the two apart: {@code requested} is the distance the caller asked the value to move,
 * {@code applied} the distance it moved, and {@code before}/{@code after} are on the axis the
 * caller spoke in -- fatigue when the mutation said fatigue, although Townstead's API only speaks
 * energy.
 *
 * <h2>Drift</h2>
 *
 * <p>The API promises to be frozen once shipped, and this adapter was written against a pinned
 * revision of it. Should a Townstead build nevertheless change a signature this adapter calls, the
 * JVM raises a {@link LinkageError} at that call site -- inside a quest tick, a reward grant or an
 * event -- so every entry point catches it: the read answers empty, the mutation fails as
 * {@link TownsteadMutationResult.Reason#INTERNAL_ERROR}, the member is named once in the log and
 * in {@link #unresolvedMembers()}, and nothing else is affected. That is the same unit of failure
 * the reflective bridge has always had, one capability at a time.
 */
public final class ApiTownsteadBridge implements TownsteadBridge {

    /** Members that raised a {@link LinkageError}, for the log and the status command. */
    private static final Set<String> DRIFTED = ConcurrentHashMap.newKeySet();

    /** Every write this mod makes is attributed to this source, so a server can refuse it in Townstead's config. */
    public static final ResourceLocation SOURCE = new ResourceLocation("mcaquests", "quests");

    /** The API generation this adapter was written against. Anything else is refused, not guessed at. */
    static final int SUPPORTED_API_VERSION = 1;

    private final TownsteadApiV1 api;
    private final String version;
    private final boolean supported;

    /** Production: resolves the live API. Throws when Townstead's implementation is missing, which the caller reports. */
    public ApiTownsteadBridge() {
        this(TownsteadApiV1.get());
    }

    /** Test seam: any {@link TownsteadApiV1}. */
    ApiTownsteadBridge(TownsteadApiV1 api) {
        this.api = api;
        this.version = api.getModVersion();
        this.supported = api.getApiVersion() == SUPPORTED_API_VERSION;
    }

    TownsteadApiV1 api() {
        return api;
    }

    @Override
    public void onBound() {
        if (supported) {
            ApiTownsteadEvents.start(api);
        }
    }

    @Override
    public void onUnbound() {
        ApiTownsteadEvents.stop();
    }

    @Override
    public TownsteadStatus status() {
        return supported ? TownsteadStatus.FULL : TownsteadStatus.DISABLED;
    }

    @Override
    public Set<TownsteadCapability> capabilities() {
        return supported ? EnumSet.allOf(TownsteadCapability.class) : Set.of();
    }

    @Override
    public String detectedVersion() {
        return version;
    }

    @Override
    public Optional<String> variant() {
        return Optional.of("api-v" + api.getApiVersion() + "-r" + api.getApiRevision());
    }

    @Override
    public String bindingPath() {
        return supported ? "api-v1"
                : "disabled: Townstead api generation " + api.getApiVersion() + " is not the generation " + SUPPORTED_API_VERSION
                + " this build supports";
    }

    @Override
    public List<String> unresolvedMembers() {
        List<String> out = new ArrayList<>();
        if (!supported) {
            out.add("api generation " + api.getApiVersion());
        }
        out.addAll(DRIFTED);
        return List.copyOf(out);
    }

    /** Runs one API call, turning API drift into the fallback rather than an exception up the tick. */
    private static <T> T guarded(String member, Supplier<T> body, T fallback) {
        try {
            return body.get();
        } catch (LinkageError drift) {
            reportDrift(member, drift);
            return fallback;
        }
    }

    /** Test seam: forget recorded drift so one test's fault does not show in another's status. */
    static void resetDriftForTest() {
        DRIFTED.clear();
    }

    static void reportDrift(String member, LinkageError drift) {
        if (DRIFTED.add(member)) {
            McaQuests.LOGGER.warn("[MCA: Quests] Townstead's api.v1 no longer matches the revision this build was "
                    + "compiled against at {} ({}); that feature is disabled until the mod is rebuilt against the "
                    + "installed Townstead. Please report this with your Townstead version.", member, drift.toString());
        }
    }

    // --- reads -----------------------------------------------------------------------------------

    @Override
    public Optional<TownsteadVillagerView> villager(Entity entity) {
        if (!supported || entity == null) {
            return Optional.empty();
        }
        TownsteadCounters.villagerRead();
        return guarded("villagers.snapshot", () -> api.villagers().snapshot(entity).map(ApiTownsteadBridge::villagerView),
                Optional.empty());
    }

    @Override
    public Optional<TownsteadCalendarView> calendar(MinecraftServer server) {
        if (!supported || server == null) {
            return Optional.empty();
        }
        return guarded("calendar.today", () -> {
            CalendarSnapshot c = api.calendar().today(server);
            // The API answers a zeroed snapshot rather than empty when the calendar cannot be read.
            if (c == null || (c.profileId().isEmpty() && c.worldDay() == 0L && c.daysInYear() == 0)) {
                return Optional.<TownsteadCalendarView>empty();
            }
            return Optional.of(calendarView(c));
        }, Optional.empty());
    }

    @Override
    public Optional<TownsteadBuildingView> buildingAt(ServerLevel level, BlockPos pos) {
        if (!supported || level == null || pos == null) {
            return Optional.empty();
        }
        return guarded("villages.buildingAt", () -> api.villages().buildingAt(level, pos).map(ApiTownsteadBridge::buildingView),
                Optional.empty());
    }

    @Override
    public Optional<TownsteadRootView> root(ResourceLocation id) {
        if (!supported || id == null) {
            return Optional.empty();
        }
        return guarded("social.root", () -> api.social().root(id).map(ApiTownsteadBridge::rootView), Optional.empty());
    }

    @Override
    public Optional<TownsteadGeneView> gene(ResourceLocation id) {
        if (!supported || id == null) {
            return Optional.empty();
        }
        return guarded("social.gene", () -> api.social().gene(id).map(ApiTownsteadBridge::geneView), Optional.empty());
    }

    @Override
    public Optional<TownsteadSpiritView> spiritForVillage(ServerLevel level, int villageId) {
        if (!supported || level == null || level.getServer() == null) {
            return Optional.empty();
        }
        VillageId id = new VillageId(level.dimension().location(), villageId);
        return guarded("villages.spirit", () -> api.villages().spirit(level.getServer(), id).map(s -> spiritView(villageId, s)),
                Optional.empty());
    }

    @Override
    public Optional<TownsteadResidentRecordView> lastKnownResident(MinecraftServer server, UUID villager) {
        if (!supported || server == null || villager == null) {
            return Optional.empty();
        }
        return guarded("villagers.record", () -> api.villagers().record(server, villager).map(ApiTownsteadBridge::recordView),
                Optional.empty());
    }

    @Override
    public Set<ResourceLocation> learnedSkills(Entity villager) {
        if (!supported || villager == null) {
            return Set.of();
        }
        return guarded("professions.learnedSkills", () -> api.professions().learnedSkills(villager), Set.of());
    }

    @Override
    public boolean hasSkill(Entity villager, ResourceLocation skillId) {
        return supported && villager != null && skillId != null
                && guarded("professions.hasSkill", () -> api.professions().hasSkill(villager, skillId), false);
    }

    @Override
    public boolean isKnownSpirit(String spiritId) {
        return supported && spiritId != null && guarded("villages.isKnownSpirit", () -> api.villages().isKnownSpirit(spiritId), false);
    }

    @Override
    public TownsteadProfessionTrackView professionTrack(String professionId) {
        String requested = professionId == null ? "" : professionId;
        if (!supported || requested.isEmpty()) {
            return TownsteadProfessionTrackView.none(requested);
        }
        // The API answers empty for a profession with no progression at all, which is precisely the
        // zero/default-spec case the reflective bridge had to reverse-engineer. Reported under the id
        // the caller asked with, as the reflective bridge does, so callers' own caches stay keyed.
        Optional<ProgressionTrackSnapshot> track = guarded("professions.track", () -> api.professions().track(requested),
                Optional.empty());
        if (track.isEmpty()) {
            return TownsteadProfessionTrackView.none(requested);
        }
        ProgressionTrackSnapshot t = track.get();
        if (t.maxTier() <= 0 || t.maxXp() <= 0) {
            return TownsteadProfessionTrackView.none(requested);
        }
        // One entry per tier, tier one first: the same shape the reflective bridge recovers by
        // bisecting Townstead's tier arithmetic. Every profession the API knows is in Townstead's
        // data-driven registry, built in or from a pack, so the registry-known flag is true.
        return new TownsteadProfessionTrackView(requested, t.tierThresholds(), t.maxTier(), t.maxXp(),
                Math.max(0, t.dailyXpCap()), true);
    }

    @Override
    public boolean isKnownSkill(ResourceLocation skillId) {
        return supported && skillId != null && guarded("professions.isKnownSkill", () -> api.professions().isKnownSkill(skillId), false);
    }

    @Override
    public Set<ResourceLocation> knownSkillIds() {
        return supported ? guarded("professions.skillIds", () -> api.professions().skillIds(), Set.of()) : Set.of();
    }

    // --- mutations -------------------------------------------------------------------------------

    @Override
    public TownsteadMutationResult changeNeeds(Entity villager, NeedMutation mutation) {
        if (mutation == null || mutation.need() == null || mutation.mode() == null
                || !Double.isFinite(mutation.amount())) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INVALID_VALUE);
        }
        if (!supported) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (villager == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        String needId = switch (mutation.need()) {
            case HUNGER -> NeedsSnapshot.HUNGER;
            case SATURATION -> NeedsSnapshot.SATURATION;
            case THIRST -> NeedsSnapshot.THIRST;
            case QUENCHED -> NeedsSnapshot.QUENCHED;
            case FATIGUE, ENERGY -> NeedsSnapshot.ENERGY;
        };
        // Fatigue is energy the other way up; Townstead's API only speaks energy. Converted on the way
        // in and converted back on the way out, so the caller's numbers stay on the caller's axis.
        boolean inverted = mutation.need() == NeedMutation.Need.FATIGUE;
        double amount = mutation.amount();
        if (inverted) {
            amount = mutation.mode() == NeedMutation.Mode.DELTA ? -amount : TownsteadNeedsView.FATIGUE_MAX - amount;
        }
        int value = (int) Math.round(amount);
        boolean delta = mutation.mode() == NeedMutation.Mode.DELTA;
        NeedResult result = guarded(delta ? "villagers.adjustNeed" : "villagers.setNeed", () -> delta
                ? api.villagers().adjustNeed(villager, needId, value, SOURCE)
                : api.villagers().setNeed(villager, needId, value, SOURCE), null);
        if (result == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }
        double before = inverted ? TownsteadNeedsView.FATIGUE_MAX - result.before() : result.before();
        double after = inverted ? TownsteadNeedsView.FATIGUE_MAX - result.after() : result.after();
        return switch (result.status()) {
            case APPLIED -> {
                // "Requested" is how far the caller asked the value to move, which for a target is the
                // distance from where it was -- the reflective bridge's definition, kept on purpose.
                int requested = mutation.mode() == NeedMutation.Mode.DELTA
                        ? Math.abs(value)
                        : (int) Math.round(Math.abs(mutation.amount() - before));
                int applied = Math.abs(result.after() - result.before());
                yield TownsteadMutationResult.success(requested, applied, before, after);
            }
            case NO_CHANGE -> TownsteadMutationResult.noChange(before);
            case GATED, DISABLED -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.FEATURE_GATED);
            case UNKNOWN_NEED -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INVALID_VALUE);
            case NOT_A_VILLAGER -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
            default -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        };
    }

    @Override
    public TownsteadMutationResult awardProfessionXp(Entity villager, String professionId, int requestedXp,
                                                     boolean respectDailyCap) {
        if (!supported) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (requestedXp <= 0 || professionId == null || professionId.isBlank()) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INVALID_VALUE);
        }
        if (villager == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        XpResult result = guarded("professions.awardXp",
                () -> api.professions().awardXp(villager, professionId, requestedXp, respectDailyCap, SOURCE), null);
        if (result == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }
        return switch (result.status()) {
            // A partial award is a success with applied < requested, exactly as the reflective path reports it.
            case APPLIED -> TownsteadMutationResult.xp(requestedXp, result.applied(), result.xpBefore(),
                    result.xpAfter(), result.tierBefore(), result.tierAfter());
            case DAILY_CAP -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.DAILY_CAP);
            case AT_MAX, INVALID, NO_PROGRESSION ->
                    TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INVALID_VALUE);
            case NOT_A_VILLAGER -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
            case DISABLED -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.FEATURE_GATED);
            default -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        };
    }

    @Override
    public TownsteadMutationResult learnSkill(Entity villager, ResourceLocation skillId, boolean force) {
        if (!supported) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (villager == null || skillId == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        return skill(guarded("professions.learnSkill", () -> api.professions().learnSkill(villager, skillId, force, SOURCE), null), 0, 1);
    }

    @Override
    public TownsteadMutationResult forgetSkill(Entity villager, ResourceLocation skillId) {
        if (!supported) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (villager == null || skillId == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        // Never forced: a profession's retraining lock is Townstead's policy, and the reflective
        // bridge never overrode it either. It comes back as a gate.
        return skill(guarded("professions.forgetSkill", () -> api.professions().forgetSkill(villager, skillId, false, SOURCE), null), 1, 0);
    }

    @Override
    public TownsteadMutationResult dispatchTransition(ServerLevel level, LivingEntity villager,
                                                      ResourceLocation taskId, String phase) {
        if (!supported) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (level == null || villager == null || taskId == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        Integer played = guarded("social.dispatchTaskTransition",
                () -> api.social().dispatchTaskTransition(level, villager, taskId, phase == null ? "" : phase), null);
        if (played == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }
        return played > 0 ? TownsteadMutationResult.success(1, played, 0, played) : TownsteadMutationResult.noChange(0);
    }

    private static TownsteadMutationResult skill(SkillResult result, int before, int after) {
        if (result == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }
        return switch (result.status()) {
            case APPLIED -> TownsteadMutationResult.success(1, 1, before, after);
            case ALREADY -> TownsteadMutationResult.noChange(after);
            case UNKNOWN_SKILL -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INVALID_VALUE);
            case PREREQUISITES_UNMET, LOCKED, DISABLED ->
                    TownsteadMutationResult.failed(TownsteadMutationResult.Reason.FEATURE_GATED);
            case NOT_A_VILLAGER -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
            default -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        };
    }

    // --- conversions -----------------------------------------------------------------------------

    /** {@code dock_l3} to {@code dock}: the same parse Townstead publishes as {@code BuildingSnapshot.family}. */
    static String family(String type) {
        return TownsteadBuildingView.familyOf(type);
    }

    static TownsteadVillagerView villagerView(VillagerSnapshot v) {
        return new TownsteadVillagerView(v.uuid(), v.name(), v.entityType(), v.rootId(), v.lifeStage(),
                v.biologicalAgeDays(), v.apparentAgeYears(), v.immortal(), v.ageless(), v.senior(), v.personalityId(),
                v.professionId(), v.professionTier(), v.professionXp(), v.fertility(), scheduleView(v.schedule()),
                needsView(v.needs()), v.carriedVariants(), v.expressedAlleles(), v.heritage());
    }

    static TownsteadNeedsView needsView(NeedsSnapshot n) {
        if (n == null) {
            return new TownsteadNeedsView(0, 0f, 0f, 0, 0, 0f, TownsteadNeedsView.FATIGUE_MAX, false, true);
        }
        return new TownsteadNeedsView(n.hunger(), n.saturation(), n.hungerExhaustion(), n.thirst(), n.quenched(),
                n.thirstExhaustion(), fatigueOf(n.energy()), n.collapsed(), !n.thirstSimulated());
    }

    /**
     * The register keeps only what it stores: hunger, thirst, energy, collapse. Saturation, quenched
     * and the exhaustion counters read as zero, which is why record-based judgements compare only
     * the four. Thirst reads as gated when the register says the need is not simulated.
     */
    static TownsteadResidentRecordView recordView(VillagerRecord r) {
        Map<String, NeedLevel> levels = r.levels();
        NeedLevel hunger = levels.get(NeedsSnapshot.HUNGER);
        NeedLevel thirst = levels.get(NeedsSnapshot.THIRST);
        NeedLevel energy = levels.get(NeedsSnapshot.ENERGY);
        boolean thirstSimulated = thirst != null && thirst.enabled();
        TownsteadNeedsView needs = new TownsteadNeedsView(
                hunger == null ? 0 : hunger.value(), 0f, 0f,
                thirst == null ? 0 : thirst.value(), 0, 0f,
                energy == null ? TownsteadNeedsView.FATIGUE_MAX : fatigueOf(energy.value()),
                r.collapsed(), !thirstSimulated);
        return new TownsteadResidentRecordView(r.uuid(), r.name(),
                r.village().map(VillageId::dimension).orElse(null),
                r.village().map(VillageId::villageId).orElse(-1),
                r.professionId(), r.professionTier(), needs, r.loaded(), r.alive(),
                r.lastSeenGameTime(), r.lastSeenWorldDay());
    }

    /** Energy on Townstead's rising scale back to fatigue on the falling one this mod's views keep. */
    static int fatigueOf(int energy) {
        return TownsteadNeedsView.FATIGUE_MAX - Math.max(0, Math.min(TownsteadNeedsView.FATIGUE_MAX, energy));
    }

    static TownsteadScheduleView scheduleView(ScheduleSnapshot s) {
        if (s == null) {
            return new TownsteadScheduleView("", "", false, false, 0, 0, 0, "", "", "", List.of(), List.of());
        }
        return new TownsteadScheduleView(s.mode(), s.templateId(), s.customShifts(), s.nonDefaultCustomShifts(),
                s.currentTickHour(), s.currentDisplayHour(), s.currentShiftOrdinal(), s.currentActivity(),
                s.plannedActivity(), s.currentTemplateId(), s.shifts(), s.weekDayTemplates());
    }

    static TownsteadBuildingView buildingView(BuildingSnapshot b) {
        return new TownsteadBuildingView(b.id(), b.village().villageId(), b.type(), b.size(),
                b.center().getX(), b.center().getY(), b.center().getZ(),
                b.min().getX(), b.min().getY(), b.min().getZ(),
                b.max().getX(), b.max().getY(), b.max().getZ());
    }

    static TownsteadSpiritView spiritView(int villageId, SpiritSnapshot s) {
        return new TownsteadSpiritView(villageId, s.perSpirit(), s.total(), s.contributingBuildings(), s.tierIndex(),
                s.classification(), s.primarySpiritId().orElse(""), s.secondarySpiritId().orElse(""));
    }

    static TownsteadCalendarView calendarView(CalendarSnapshot c) {
        return new TownsteadCalendarView(c.profileId(), c.worldDay(), c.epochYearOffset(), c.timeMode(), c.year(),
                c.month(), c.day(), c.dayOfYear(), c.dayOfWeek(), c.season());
    }

    static TownsteadRootView rootView(RootSnapshot r) {
        List<TownsteadLifeStageView> stages = new ArrayList<>();
        for (RootSnapshot.LifeStageInfo stage : r.lifeStages()) {
            stages.add(new TownsteadLifeStageView(stage.id(), stage.label(), stage.days(), stage.scale(),
                    stage.presentsAs(), stage.narrativeStart(), stage.narrativeEnd()));
        }
        return new TownsteadRootView(r.id(), r.displayName(), r.species(), r.ancestry(), r.lineage(),
                r.effectiveSpecies(), r.defaultGenes(), stages);
    }

    static TownsteadGeneView geneView(GeneSnapshot g) {
        List<TownsteadGeneVariantView> variants = new ArrayList<>();
        for (GeneSnapshot.VariantInfo v : g.variants()) {
            variants.add(new TownsteadGeneVariantView(v.id(), v.displayName(), v.weight(), v.type()));
        }
        return new TownsteadGeneView(g.id(), g.displayName(), g.description(), g.category(), g.dominance(), g.locus(),
                g.weight(), g.displayMode(), variants);
    }
}
