package dev.otectus.mcaquests.compat.townstead.v1;

import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.model.BuildingSnapshot;
import com.aetherianartificer.townstead.api.v1.model.CalendarSnapshot;
import com.aetherianartificer.townstead.api.v1.model.GeneSnapshot;
import com.aetherianartificer.townstead.api.v1.model.NeedsSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProgressionTrackSnapshot;
import com.aetherianartificer.townstead.api.v1.model.RootSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ScheduleSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SpiritSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillagerSnapshot;
import com.aetherianartificer.townstead.api.v1.result.NeedResult;
import com.aetherianartificer.townstead.api.v1.result.SkillResult;
import com.aetherianartificer.townstead.api.v1.result.XpResult;
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
import java.util.Optional;
import java.util.Set;

/**
 * The typed Townstead bridge, for Townstead builds that ship {@code api.v1}. Every method is a
 * direct call into a frozen, versioned surface that names no MCA type, so nothing here can be
 * linked to an MCA package layout. Chosen by {@code TownsteadCompat} when the API class is
 * present; older Townstead builds keep the reflective bridge.
 *
 * <p>Only ever loaded after the mod-present check, which is why it may import Townstead types
 * freely. {@code NoTownsteadStaticLinkTest} exempts this package for the same reason it exempts
 * the Reputation adapter.
 */
public final class ApiTownsteadBridge implements TownsteadBridge {

    /** Every write this mod makes is attributed to this source, so a server can refuse it in Townstead's config. */
    public static final ResourceLocation SOURCE = new ResourceLocation("mcaquests", "quests");

    private final TownsteadApiV1 api;
    private final String version;

    public ApiTownsteadBridge() {
        this.api = TownsteadApiV1.get();
        this.version = api.getModVersion();
        if (api.getApiVersion() == 1) {
            ApiTownsteadEvents.start(api);
        }
    }

    @Override
    public TownsteadStatus status() {
        return api.getApiVersion() == 1 ? TownsteadStatus.FULL : TownsteadStatus.DISABLED;
    }

    @Override
    public Set<TownsteadCapability> capabilities() {
        return api.getApiVersion() == 1 ? EnumSet.allOf(TownsteadCapability.class) : Set.of();
    }

    @Override
    public String detectedVersion() {
        return version;
    }

    @Override
    public Optional<String> variant() {
        return Optional.of("api-v1-r" + api.getApiRevision());
    }

    // --- reads -----------------------------------------------------------------------------------

    @Override
    public Optional<TownsteadVillagerView> villager(Entity entity) {
        TownsteadCounters.villagerRead();
        return api.villagers().snapshot(entity).map(ApiTownsteadBridge::villagerView);
    }

    @Override
    public Optional<TownsteadCalendarView> calendar(MinecraftServer server) {
        CalendarSnapshot c = api.calendar().today(server);
        if (c.profileId().isEmpty() && c.worldDay() == 0L) return Optional.empty();
        return Optional.of(new TownsteadCalendarView(c.profileId(), c.worldDay(), c.epochYearOffset(), c.timeMode(),
                c.year(), c.month(), c.day(), c.dayOfYear(), c.dayOfWeek(), c.season()));
    }

    @Override
    public Optional<TownsteadBuildingView> buildingAt(ServerLevel level, BlockPos pos) {
        return api.villages().buildingAt(level, pos).map(ApiTownsteadBridge::buildingView);
    }

    @Override
    public Optional<TownsteadRootView> root(ResourceLocation id) {
        return api.social().root(id).map(ApiTownsteadBridge::rootView);
    }

    @Override
    public Optional<TownsteadGeneView> gene(ResourceLocation id) {
        return api.social().gene(id).map(ApiTownsteadBridge::geneView);
    }

    @Override
    public Optional<TownsteadSpiritView> spiritForVillage(ServerLevel level, int villageId) {
        VillageId id = new VillageId(level.dimension().location(), villageId);
        return api.villages().spirit(level.getServer(), id).map(s -> spiritView(villageId, s));
    }

    @Override
    public Set<ResourceLocation> learnedSkills(Entity villager) {
        return api.professions().learnedSkills(villager);
    }

    @Override
    public boolean hasSkill(Entity villager, ResourceLocation skillId) {
        return api.professions().hasSkill(villager, skillId);
    }

    @Override
    public boolean isKnownSpirit(String spiritId) {
        return api.villages().isKnownSpirit(spiritId);
    }

    @Override
    public TownsteadProfessionTrackView professionTrack(String professionId) {
        Optional<ProgressionTrackSnapshot> track = api.professions().track(professionId);
        if (track.isEmpty()) return TownsteadProfessionTrackView.none(professionId);
        ProgressionTrackSnapshot t = track.get();
        // Both lists hold one entry per tier, tier one first: exactly what the reflective bridge
        // used to recover by bisecting Townstead's tier arithmetic.
        return new TownsteadProfessionTrackView(t.professionId(), t.tierThresholds(), t.maxTier(), t.maxXp(),
                t.dailyXpCap(), true);
    }

    @Override
    public boolean isKnownSkill(ResourceLocation skillId) {
        return api.professions().isKnownSkill(skillId);
    }

    @Override
    public Set<ResourceLocation> knownSkillIds() {
        return api.professions().skillIds();
    }

    // --- mutations -------------------------------------------------------------------------------

    @Override
    public TownsteadMutationResult changeNeeds(Entity villager, NeedMutation mutation) {
        if (mutation == null || mutation.need() == null || mutation.mode() == null
                || !Double.isFinite(mutation.amount())) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INVALID_VALUE);
        }
        String needId = switch (mutation.need()) {
            case HUNGER -> NeedsSnapshot.HUNGER;
            case SATURATION -> NeedsSnapshot.SATURATION;
            case THIRST -> NeedsSnapshot.THIRST;
            case QUENCHED -> NeedsSnapshot.QUENCHED;
            case FATIGUE, ENERGY -> NeedsSnapshot.ENERGY;
        };
        // Fatigue is energy the other way up; Townstead only speaks energy.
        double amount = mutation.amount();
        if (mutation.need() == NeedMutation.Need.FATIGUE) {
            amount = mutation.mode() == NeedMutation.Mode.DELTA ? -amount : TownsteadNeedsView.FATIGUE_MAX - amount;
        }
        int value = (int) Math.round(amount);
        NeedResult result = mutation.mode() == NeedMutation.Mode.DELTA
                ? api.villagers().adjustNeed(villager, needId, value, SOURCE)
                : api.villagers().setNeed(villager, needId, value, SOURCE);
        return switch (result.status()) {
            case APPLIED -> TownsteadMutationResult.success(Math.abs(value), Math.abs(result.after() - result.before()),
                    result.before(), result.after());
            case NO_CHANGE -> TownsteadMutationResult.noChange(result.before());
            case GATED -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.FEATURE_GATED);
            case UNKNOWN_NEED -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INVALID_VALUE);
            case NOT_A_VILLAGER -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
            case DISABLED -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.FEATURE_GATED);
            default -> TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        };
    }

    @Override
    public TownsteadMutationResult awardProfessionXp(Entity villager, String professionId, int requestedXp,
                                                     boolean respectDailyCap) {
        XpResult result = api.professions().awardXp(villager, professionId, requestedXp, respectDailyCap, SOURCE);
        return switch (result.status()) {
            case APPLIED -> TownsteadMutationResult.xp(result.requested(), result.applied(), result.xpBefore(),
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
        return skill(api.professions().learnSkill(villager, skillId, force, SOURCE), 0, 1);
    }

    @Override
    public TownsteadMutationResult forgetSkill(Entity villager, ResourceLocation skillId) {
        return skill(api.professions().forgetSkill(villager, skillId, false, SOURCE), 1, 0);
    }

    @Override
    public TownsteadMutationResult dispatchTransition(ServerLevel level, LivingEntity villager,
                                                      ResourceLocation taskId, String phase) {
        int played = api.social().dispatchTaskTransition(level, villager, taskId, phase);
        return played > 0 ? TownsteadMutationResult.success(1, played, 0, played) : TownsteadMutationResult.noChange(0);
    }

    private static TownsteadMutationResult skill(SkillResult result, int before, int after) {
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
        return new TownsteadNeedsView(n.hunger(), n.saturation(), n.hungerExhaustion(), n.thirst(), n.quenched(),
                n.thirstExhaustion(), TownsteadNeedsView.FATIGUE_MAX - n.energy(), n.collapsed(), !n.thirstSimulated());
    }

    static TownsteadScheduleView scheduleView(ScheduleSnapshot s) {
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
