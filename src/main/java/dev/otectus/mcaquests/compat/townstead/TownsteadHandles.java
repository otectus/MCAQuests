package dev.otectus.mcaquests.compat.townstead;

import dev.otectus.mcaquests.compat.NeedMutation;
import dev.otectus.mcaquests.compat.TownsteadBuildingView;
import dev.otectus.mcaquests.compat.TownsteadCalendarView;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadGeneVariantView;
import dev.otectus.mcaquests.compat.TownsteadGeneView;
import dev.otectus.mcaquests.compat.TownsteadLifeStageView;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadRootView;
import dev.otectus.mcaquests.compat.TownsteadScheduleView;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.compat.TownsteadXpMath;
import dev.otectus.mcaquests.compat.mca.McaHandles;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The typed facade over {@link TownsteadBinding}'s resolved handles: Townstead objects go in, MCA:
 * Quests' own view records come out, and <b>no Townstead value ever escapes this class</b>.
 *
 * <p>Handles are {@code private static final} so HotSpot can constant-fold them at the call site; a
 * per-call map lookup would forfeit that, and these run inside the once-per-second objective pass.
 * Every read swallows {@link Throwable} and returns the type's empty value, because an unbound member
 * is a stub whose invocation is legal but meaningless, and because a read is never worth a crash.
 *
 * <p>Enums are converted with {@link Enum#name()} lowercased rather than returned as-is, so a
 * Townstead enum constant cannot leak into a view record and become linkage.
 *
 * @see TownsteadBinding for the manifest these handles come from
 */
final class TownsteadHandles {

    private static final TownsteadBinding.Resolution R = resolveQuietly();

    private static TownsteadBinding.Resolution resolveQuietly() {
        try {
            return TownsteadBinding.resolveAgainst(TownsteadHandles.class.getClassLoader());
        } catch (Throwable t) {
            return TownsteadBinding.absent();
        }
    }

    /** The live resolution, for the status command and the one line logged at startup. */
    static TownsteadBinding.Resolution resolution() {
        return R;
    }

    static boolean has(TownsteadCapability capability) {
        return R.has(capability);
    }

    // --- handles ---------------------------------------------------------------------------------

    private static final MethodHandle H_ENTITY = R.handle(TownsteadBinding.API_ENTITY);
    private static final MethodHandle H_CALENDAR = R.handle(TownsteadBinding.API_CALENDAR);
    private static final MethodHandle H_BUILDING_AT = R.handle(TownsteadBinding.API_BUILDING_AT);
    private static final MethodHandle H_ORIGIN = R.handle(TownsteadBinding.API_ORIGIN);
    private static final MethodHandle H_GENE = R.handle(TownsteadBinding.API_GENE);

    private static final MethodHandle H_V_UUID = R.handle(TownsteadBinding.V_UUID);
    private static final MethodHandle H_V_NAME = R.handle(TownsteadBinding.V_NAME);
    private static final MethodHandle H_V_ENTITY_TYPE = R.handle(TownsteadBinding.V_ENTITY_TYPE);
    private static final MethodHandle H_V_ROOT_ID = R.handle(TownsteadBinding.V_ROOT_ID);
    private static final MethodHandle H_V_LIFE_STAGE = R.handle(TownsteadBinding.V_LIFE_STAGE);
    private static final MethodHandle H_V_AGE_DAYS = R.handle(TownsteadBinding.V_AGE_DAYS);
    private static final MethodHandle H_V_AGE_YEARS = R.handle(TownsteadBinding.V_AGE_YEARS);
    private static final MethodHandle H_V_IMMORTAL = R.handle(TownsteadBinding.V_IMMORTAL);
    private static final MethodHandle H_V_AGELESS = R.handle(TownsteadBinding.V_AGELESS);
    private static final MethodHandle H_V_SENIOR = R.handle(TownsteadBinding.V_SENIOR);
    private static final MethodHandle H_V_PERSONALITY = R.handle(TownsteadBinding.V_PERSONALITY);
    private static final MethodHandle H_V_FERTILITY = R.handle(TownsteadBinding.V_FERTILITY);
    private static final MethodHandle H_V_CARRIED = R.handle(TownsteadBinding.V_CARRIED);
    private static final MethodHandle H_V_ALLELES = R.handle(TownsteadBinding.V_ALLELES);
    private static final MethodHandle H_V_HERITAGE = R.handle(TownsteadBinding.V_HERITAGE);
    private static final MethodHandle H_V_PROFESSION_ID = R.handle(TownsteadBinding.V_PROFESSION_ID);
    private static final MethodHandle H_V_PROFESSION_LEVEL = R.handle(TownsteadBinding.V_PROFESSION_LEVEL);
    private static final MethodHandle H_V_PROFESSION_XP = R.handle(TownsteadBinding.V_PROFESSION_XP);
    private static final MethodHandle H_V_NEEDS = R.handle(TownsteadBinding.V_NEEDS);
    private static final MethodHandle H_V_SCHEDULE = R.handle(TownsteadBinding.V_SCHEDULE);

    private static final MethodHandle H_N_HUNGER = R.handle(TownsteadBinding.N_HUNGER);
    private static final MethodHandle H_N_SATURATION = R.handle(TownsteadBinding.N_SATURATION);
    private static final MethodHandle H_N_HUNGER_EXH = R.handle(TownsteadBinding.N_HUNGER_EXHAUSTION);
    private static final MethodHandle H_N_THIRST = R.handle(TownsteadBinding.N_THIRST);
    private static final MethodHandle H_N_QUENCHED = R.handle(TownsteadBinding.N_QUENCHED);
    private static final MethodHandle H_N_THIRST_EXH = R.handle(TownsteadBinding.N_THIRST_EXHAUSTION);
    private static final MethodHandle H_N_FATIGUE = R.handle(TownsteadBinding.N_FATIGUE);
    private static final MethodHandle H_N_COLLAPSED = R.handle(TownsteadBinding.N_COLLAPSED);
    private static final MethodHandle H_N_GATED = R.handle(TownsteadBinding.N_GATED);

    private static final MethodHandle H_S_MODE = R.handle(TownsteadBinding.S_MODE);
    private static final MethodHandle H_S_TEMPLATE_ID = R.handle(TownsteadBinding.S_TEMPLATE_ID);
    private static final MethodHandle H_S_CUSTOM_SHIFTS = R.handle(TownsteadBinding.S_CUSTOM_SHIFTS);
    private static final MethodHandle H_S_NON_DEFAULT = R.handle(TownsteadBinding.S_NON_DEFAULT_SHIFTS);
    private static final MethodHandle H_S_TICK_HOUR = R.handle(TownsteadBinding.S_TICK_HOUR);
    private static final MethodHandle H_S_DISPLAY_HOUR = R.handle(TownsteadBinding.S_DISPLAY_HOUR);
    private static final MethodHandle H_S_SHIFT_ORDINAL = R.handle(TownsteadBinding.S_SHIFT_ORDINAL);
    private static final MethodHandle H_S_CURRENT_ACTIVITY = R.handle(TownsteadBinding.S_CURRENT_ACTIVITY);
    private static final MethodHandle H_S_PLANNED_ACTIVITY = R.handle(TownsteadBinding.S_PLANNED_ACTIVITY);
    private static final MethodHandle H_S_CURRENT_TEMPLATE = R.handle(TownsteadBinding.S_CURRENT_TEMPLATE);
    private static final MethodHandle H_S_SHIFTS = R.handle(TownsteadBinding.S_SHIFTS);
    private static final MethodHandle H_S_WEEKDAYS = R.handle(TownsteadBinding.S_WEEKDAY_TEMPLATES);

    private static final MethodHandle H_K_PROFILE_ID = R.handle(TownsteadBinding.K_PROFILE_ID);
    private static final MethodHandle H_K_WORLD_DAY = R.handle(TownsteadBinding.K_WORLD_DAY);
    private static final MethodHandle H_K_EPOCH_OFFSET = R.handle(TownsteadBinding.K_EPOCH_OFFSET);
    private static final MethodHandle H_K_TIME_MODE = R.handle(TownsteadBinding.K_TIME_MODE);
    private static final MethodHandle H_K_YEAR = R.handle(TownsteadBinding.K_YEAR);
    private static final MethodHandle H_K_MONTH = R.handle(TownsteadBinding.K_MONTH);
    private static final MethodHandle H_K_DAY = R.handle(TownsteadBinding.K_DAY);
    private static final MethodHandle H_K_DAY_OF_YEAR = R.handle(TownsteadBinding.K_DAY_OF_YEAR);
    private static final MethodHandle H_K_DAY_OF_WEEK = R.handle(TownsteadBinding.K_DAY_OF_WEEK);
    private static final MethodHandle H_K_SEASON = R.handle(TownsteadBinding.K_SEASON);

    private static final MethodHandle H_B_ID = R.handle(TownsteadBinding.B_ID);
    private static final MethodHandle H_B_VILLAGE_ID = R.handle(TownsteadBinding.B_VILLAGE_ID);
    private static final MethodHandle H_B_TYPE = R.handle(TownsteadBinding.B_TYPE);
    private static final MethodHandle H_B_SIZE = R.handle(TownsteadBinding.B_SIZE);
    private static final MethodHandle H_B_CENTER_X = R.handle(TownsteadBinding.B_CENTER_X);
    private static final MethodHandle H_B_CENTER_Y = R.handle(TownsteadBinding.B_CENTER_Y);
    private static final MethodHandle H_B_CENTER_Z = R.handle(TownsteadBinding.B_CENTER_Z);
    private static final MethodHandle H_B_MIN_X = R.handle(TownsteadBinding.B_MIN_X);
    private static final MethodHandle H_B_MIN_Y = R.handle(TownsteadBinding.B_MIN_Y);
    private static final MethodHandle H_B_MIN_Z = R.handle(TownsteadBinding.B_MIN_Z);
    private static final MethodHandle H_B_MAX_X = R.handle(TownsteadBinding.B_MAX_X);
    private static final MethodHandle H_B_MAX_Y = R.handle(TownsteadBinding.B_MAX_Y);
    private static final MethodHandle H_B_MAX_Z = R.handle(TownsteadBinding.B_MAX_Z);

    private static final MethodHandle H_R_ID = R.handle(TownsteadBinding.R_ID);
    private static final MethodHandle H_R_DISPLAY_NAME = R.handle(TownsteadBinding.R_DISPLAY_NAME);
    private static final MethodHandle H_R_SPECIES = R.handle(TownsteadBinding.R_SPECIES);
    private static final MethodHandle H_R_ANCESTRY = R.handle(TownsteadBinding.R_ANCESTRY);
    private static final MethodHandle H_R_LINEAGE = R.handle(TownsteadBinding.R_LINEAGE);
    private static final MethodHandle H_R_EFFECTIVE_SPECIES = R.handle(TownsteadBinding.R_EFFECTIVE_SPECIES);
    private static final MethodHandle H_R_DEFAULT_GENES = R.handle(TownsteadBinding.R_DEFAULT_GENES);
    private static final MethodHandle H_R_LIFE_STAGES = R.handle(TownsteadBinding.R_LIFE_STAGES);
    private static final MethodHandle H_LS_ID = R.handle(TownsteadBinding.LS_ID);
    private static final MethodHandle H_LS_LABEL = R.handle(TownsteadBinding.LS_LABEL);
    private static final MethodHandle H_LS_DAYS = R.handle(TownsteadBinding.LS_DAYS);
    private static final MethodHandle H_LS_SCALE = R.handle(TownsteadBinding.LS_SCALE);
    private static final MethodHandle H_LS_PRESENTS_AS = R.handle(TownsteadBinding.LS_PRESENTS_AS);
    private static final MethodHandle H_LS_NARRATIVE_START = R.handle(TownsteadBinding.LS_NARRATIVE_START);
    private static final MethodHandle H_LS_NARRATIVE_END = R.handle(TownsteadBinding.LS_NARRATIVE_END);

    private static final MethodHandle H_G_ID = R.handle(TownsteadBinding.G_ID);
    private static final MethodHandle H_G_DISPLAY_NAME = R.handle(TownsteadBinding.G_DISPLAY_NAME);
    private static final MethodHandle H_G_DESCRIPTION = R.handle(TownsteadBinding.G_DESCRIPTION);
    private static final MethodHandle H_G_CATEGORY = R.handle(TownsteadBinding.G_CATEGORY);
    private static final MethodHandle H_G_DOMINANCE = R.handle(TownsteadBinding.G_DOMINANCE);
    private static final MethodHandle H_G_LOCUS = R.handle(TownsteadBinding.G_LOCUS);
    private static final MethodHandle H_G_WEIGHT = R.handle(TownsteadBinding.G_WEIGHT);
    private static final MethodHandle H_G_DISPLAY_MODE = R.handle(TownsteadBinding.G_DISPLAY_MODE);
    private static final MethodHandle H_G_VARIANTS = R.handle(TownsteadBinding.G_VARIANTS);
    private static final MethodHandle H_GV_ID = R.handle(TownsteadBinding.GV_ID);
    private static final MethodHandle H_GV_DISPLAY_NAME = R.handle(TownsteadBinding.GV_DISPLAY_NAME);
    private static final MethodHandle H_GV_WEIGHT = R.handle(TownsteadBinding.GV_WEIGHT);
    private static final MethodHandle H_GV_TYPE = R.handle(TownsteadBinding.GV_TYPE);

    private static final MethodHandle H_SPIRIT_TOTALS_FOR = R.handle(TownsteadBinding.SPIRIT_TOTALS_FOR);
    private static final MethodHandle H_SPIRIT_READOUT_FOR = R.handle(TownsteadBinding.SPIRIT_READOUT_FOR);
    private static final MethodHandle H_ST_PER_SPIRIT = R.handle(TownsteadBinding.ST_PER_SPIRIT);
    private static final MethodHandle H_ST_TOTAL = R.handle(TownsteadBinding.ST_TOTAL);
    private static final MethodHandle H_ST_CONTRIBUTING = R.handle(TownsteadBinding.ST_CONTRIBUTING);
    private static final MethodHandle H_SR_CLASSIFICATION = R.handle(TownsteadBinding.SR_CLASSIFICATION);
    private static final MethodHandle H_SR_TIER_INDEX = R.handle(TownsteadBinding.SR_TIER_INDEX);
    private static final MethodHandle H_SR_PRIMARY = R.handle(TownsteadBinding.SR_PRIMARY);
    private static final MethodHandle H_SR_SECONDARY = R.handle(TownsteadBinding.SR_SECONDARY);
    private static final MethodHandle H_SPIRIT_CONTAINS = R.handle(TownsteadBinding.SPIRIT_CONTAINS);

    private static final MethodHandle H_VILLAGERS_GET = R.handle(TownsteadBinding.VILLAGERS_GET);
    private static final MethodHandle H_STATE_NEEDS = R.handle(TownsteadBinding.STATE_NEEDS);
    private static final MethodHandle H_SET_HUNGER = R.handle(TownsteadBinding.NEEDS_SET_HUNGER);
    private static final MethodHandle H_SET_SATURATION = R.handle(TownsteadBinding.NEEDS_SET_SATURATION);
    private static final MethodHandle H_SET_THIRST = R.handle(TownsteadBinding.NEEDS_SET_THIRST);
    private static final MethodHandle H_SET_QUENCHED = R.handle(TownsteadBinding.NEEDS_SET_QUENCHED);
    private static final MethodHandle H_SET_FATIGUE = R.handle(TownsteadBinding.NEEDS_SET_FATIGUE);
    private static final MethodHandle H_RESTORE_ENERGY = R.handle(TownsteadBinding.NEEDS_RESTORE_ENERGY);

    private static final MethodHandle H_PROFESSION_MEMORY = R.handle(TownsteadBinding.STATE_PROFESSION_MEMORY);
    private static final MethodHandle H_MEMORY_XP = R.handle(TownsteadBinding.MEMORY_PROFESSION_XP);
    private static final MethodHandle H_MEMORY_SET_XP = R.handle(TownsteadBinding.MEMORY_SET_PROFESSION_XP);
    private static final MethodHandle H_XP_NEW = R.handle(TownsteadBinding.XP_NEW);
    private static final MethodHandle H_XP_XP = R.handle(TownsteadBinding.XP_XP);
    private static final MethodHandle H_XP_TIER = R.handle(TownsteadBinding.XP_TIER);
    private static final MethodHandle H_XP_LAST_TIER_UP = R.handle(TownsteadBinding.XP_LAST_TIER_UP);
    private static final MethodHandle H_XP_DAY = R.handle(TownsteadBinding.XP_DAY);
    private static final MethodHandle H_XP_TODAY = R.handle(TownsteadBinding.XP_TODAY);
    private static final MethodHandle H_SPEC = R.handle(TownsteadBinding.PROGRESSIONS_SPEC);
    private static final MethodHandle H_SPEC_DAILY_CAP = R.handle(TownsteadBinding.SPEC_DAILY_CAP);
    private static final MethodHandle H_SPEC_MAX_XP = R.handle(TownsteadBinding.SPEC_MAX_XP);
    private static final MethodHandle H_SPEC_TIER_FOR_XP = R.handle(TownsteadBinding.SPEC_TIER_FOR_XP);
    private static final MethodHandle H_ADD_XP = R.handle(TownsteadBinding.PROGRESS_ADD_XP);
    private static final MethodHandle H_GAIN_APPLIED = R.handle(TownsteadBinding.GAIN_APPLIED);
    private static final MethodHandle H_GAIN_TIER_BEFORE = R.handle(TownsteadBinding.GAIN_TIER_BEFORE);
    private static final MethodHandle H_GAIN_TIER_AFTER = R.handle(TownsteadBinding.GAIN_TIER_AFTER);
    private static final MethodHandle H_XP_TYPE_VALUES = R.handle(TownsteadBinding.XP_TYPE_VALUES);
    private static final MethodHandle H_XP_TYPE_ID = R.handle(TownsteadBinding.XP_TYPE_ID);

    private static final MethodHandle H_SKILLS_LEARNED = R.handle(TownsteadBinding.SKILLS_LEARNED);
    private static final MethodHandle H_SKILLS_HAS = R.handle(TownsteadBinding.SKILLS_HAS);
    private static final MethodHandle H_SKILLS_LEARN = R.handle(TownsteadBinding.SKILLS_LEARN);
    private static final MethodHandle H_SKILLS_FORCE_LEARN = R.handle(TownsteadBinding.SKILLS_FORCE_LEARN);
    private static final MethodHandle H_SKILLS_FORGET = R.handle(TownsteadBinding.SKILLS_FORGET);
    private static final MethodHandle H_SKILL_OK = R.handle(TownsteadBinding.SKILL_RESULT_OK);
    private static final MethodHandle H_SKILL_ERROR = R.handle(TownsteadBinding.SKILL_RESULT_ERROR);
    private static final MethodHandle H_FORGET_OK = R.handle(TownsteadBinding.FORGET_RESULT_OK);

    private static final MethodHandle H_REACTION = R.handle(TownsteadBinding.REACTION_ON_TASK);

    // --- reads -----------------------------------------------------------------------------------

    /**
     * The Townstead snapshot of any entity, normalised. Empty for a non-villager, for a villager
     * Townstead has no state for, or when {@code READ_VILLAGER} did not bind.
     *
     * <p>An unparseable UUID yields empty rather than a view with a synthetic identity: quests freeze
     * baselines and targets on this value, and a made-up one would silently bind the wrong villager.
     */
    static Optional<TownsteadVillagerView> villager(Entity entity) {
        if (entity == null || !R.has(TownsteadCapability.READ_VILLAGER)) {
            return Optional.empty();
        }
        Object snapshot = statik(H_ENTITY, entity);
        if (snapshot == null) {
            return Optional.empty();
        }
        UUID uuid = uuid(str(H_V_UUID, snapshot));
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.of(new TownsteadVillagerView(
                uuid,
                str(H_V_NAME, snapshot),
                str(H_V_ENTITY_TYPE, snapshot),
                str(H_V_ROOT_ID, snapshot),
                str(H_V_LIFE_STAGE, snapshot),
                lng(H_V_AGE_DAYS, snapshot),
                integer(H_V_AGE_YEARS, snapshot),
                bool(H_V_IMMORTAL, snapshot),
                bool(H_V_AGELESS, snapshot),
                bool(H_V_SENIOR, snapshot),
                str(H_V_PERSONALITY, snapshot),
                str(H_V_PROFESSION_ID, snapshot),
                integer(H_V_PROFESSION_LEVEL, snapshot),
                integer(H_V_PROFESSION_XP, snapshot),
                flt(H_V_FERTILITY, snapshot),
                schedule(ref(H_V_SCHEDULE, snapshot)),
                needs(ref(H_V_NEEDS, snapshot)),
                stringMap(H_V_CARRIED, snapshot),
                stringList(H_V_ALLELES, snapshot),
                floatMap(H_V_HERITAGE, snapshot)));
    }

    static Optional<TownsteadCalendarView> calendar(MinecraftServer server) {
        if (server == null || !R.has(TownsteadCapability.READ_CALENDAR)) {
            return Optional.empty();
        }
        Object snapshot = statik(H_CALENDAR, server);
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.of(new TownsteadCalendarView(
                str(H_K_PROFILE_ID, snapshot),
                lng(H_K_WORLD_DAY, snapshot),
                integer(H_K_EPOCH_OFFSET, snapshot),
                str(H_K_TIME_MODE, snapshot),
                integer(H_K_YEAR, snapshot),
                integer(H_K_MONTH, snapshot),
                integer(H_K_DAY, snapshot),
                integer(H_K_DAY_OF_YEAR, snapshot),
                integer(H_K_DAY_OF_WEEK, snapshot),
                str(H_K_SEASON, snapshot)));
    }

    static Optional<TownsteadBuildingView> buildingAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !R.has(TownsteadCapability.READ_BUILDING)) {
            return Optional.empty();
        }
        Object snapshot = statik(H_BUILDING_AT, level, pos);
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.of(new TownsteadBuildingView(
                integer(H_B_ID, snapshot),
                integer(H_B_VILLAGE_ID, snapshot),
                str(H_B_TYPE, snapshot),
                integer(H_B_SIZE, snapshot),
                integer(H_B_CENTER_X, snapshot),
                integer(H_B_CENTER_Y, snapshot),
                integer(H_B_CENTER_Z, snapshot),
                integer(H_B_MIN_X, snapshot),
                integer(H_B_MIN_Y, snapshot),
                integer(H_B_MIN_Z, snapshot),
                integer(H_B_MAX_X, snapshot),
                integer(H_B_MAX_Y, snapshot),
                integer(H_B_MAX_Z, snapshot)));
    }

    static Optional<TownsteadRootView> root(ResourceLocation id) {
        if (id == null || !R.has(TownsteadCapability.READ_ROOT)) {
            return Optional.empty();
        }
        Object snapshot = statik(H_ORIGIN, id);
        if (snapshot == null) {
            return Optional.empty();
        }
        List<TownsteadLifeStageView> stages = new ArrayList<>();
        for (Object stage : list(H_R_LIFE_STAGES, snapshot)) {
            stages.add(new TownsteadLifeStageView(
                    str(H_LS_ID, stage),
                    str(H_LS_LABEL, stage),
                    integer(H_LS_DAYS, stage),
                    flt(H_LS_SCALE, stage),
                    str(H_LS_PRESENTS_AS, stage),
                    flt(H_LS_NARRATIVE_START, stage),
                    flt(H_LS_NARRATIVE_END, stage)));
        }
        return Optional.of(new TownsteadRootView(
                str(H_R_ID, snapshot),
                str(H_R_DISPLAY_NAME, snapshot),
                str(H_R_SPECIES, snapshot),
                str(H_R_ANCESTRY, snapshot),
                str(H_R_LINEAGE, snapshot),
                str(H_R_EFFECTIVE_SPECIES, snapshot),
                stringList(H_R_DEFAULT_GENES, snapshot),
                List.copyOf(stages)));
    }

    static Optional<TownsteadGeneView> gene(ResourceLocation id) {
        if (id == null || !R.has(TownsteadCapability.READ_GENE)) {
            return Optional.empty();
        }
        Object snapshot = statik(H_GENE, id);
        if (snapshot == null) {
            return Optional.empty();
        }
        List<TownsteadGeneVariantView> variants = new ArrayList<>();
        for (Object variant : list(H_G_VARIANTS, snapshot)) {
            variants.add(new TownsteadGeneVariantView(
                    str(H_GV_ID, variant),
                    str(H_GV_DISPLAY_NAME, variant),
                    integer(H_GV_WEIGHT, variant),
                    str(H_GV_TYPE, variant)));
        }
        return Optional.of(new TownsteadGeneView(
                str(H_G_ID, snapshot),
                str(H_G_DISPLAY_NAME, snapshot),
                str(H_G_DESCRIPTION, snapshot),
                str(H_G_CATEGORY, snapshot),
                str(H_G_DOMINANCE, snapshot),
                str(H_G_LOCUS, snapshot),
                integer(H_G_WEIGHT, snapshot),
                str(H_G_DISPLAY_MODE, snapshot),
                List.copyOf(variants)));
    }

    /**
     * Village spirit. The one read that reaches past Townstead's public facade, so it is also the one
     * that needs MCA: the {@code Village} object comes from {@link McaHandles} as a plain
     * {@link Object} and crosses into Townstead through an erased handle, so neither side's type is
     * ever named here.
     */
    static Optional<TownsteadSpiritView> spirit(ServerLevel level, int villageId) {
        if (level == null || !R.has(TownsteadCapability.READ_SPIRIT)) {
            return Optional.empty();
        }
        Object village = McaHandles.village(level, villageId);
        if (village == null) {
            return Optional.empty();
        }
        Object totals = statik(H_SPIRIT_TOTALS_FOR, village);
        if (totals == null) {
            return Optional.empty();
        }
        Object readout = statik(H_SPIRIT_READOUT_FOR, totals);
        return Optional.of(new TownsteadSpiritView(
                villageId,
                intMap(H_ST_PER_SPIRIT, totals),
                integer(H_ST_TOTAL, totals),
                integer(H_ST_CONTRIBUTING, totals),
                integer(H_SR_TIER_INDEX, readout),
                enumName(ref(H_SR_CLASSIFICATION, readout)),
                str(H_SR_PRIMARY, readout),
                str(H_SR_SECONDARY, readout)));
    }

    /** True when Townstead recognises this spirit id, so content can be validated against reality. */
    static boolean isKnownSpirit(String spiritId) {
        if (spiritId == null || spiritId.isEmpty() || !R.has(TownsteadCapability.READ_SPIRIT)) {
            return false;
        }
        try {
            return (boolean) H_SPIRIT_CONTAINS.invoke(spiritId);
        } catch (Throwable t) {
            return false;
        }
    }

    // --- mutations -------------------------------------------------------------------------------

    /**
     * Writes one need back through the setter Townstead uses itself, so its dirty and sync bookkeeping
     * stays correct, then <b>re-reads</b> to report what actually landed rather than what was asked for.
     *
     * <p>Energy is expressed through {@code restoreEnergy} rather than by writing fatigue directly.
     * That is the path Townstead uses to bring a collapsed villager back, and setting the number alone
     * would leave one standing up still carrying whatever floored them.
     */
    static TownsteadMutationResult changeNeeds(Entity villager, NeedMutation mutation) {
        if (!R.has(TownsteadCapability.MUTATE_NEEDS)) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        TownsteadNeedsView before = needsOf(villager);
        if (before == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        if (before.gated() && isThirst(mutation.need())) {
            // Townstead gates thirst behind a thirst mod. Refusing loudly beats writing a number into a
            // need nothing simulates, where it would read back unchanged and look like a broken reward.
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.FEATURE_GATED);
        }
        Object needs = needsState(villager);
        if (needs == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }

        double current = currentOf(before, mutation.need());
        double wanted = mutation.mode() == NeedMutation.Mode.DELTA ? current + mutation.amount()
                : mutation.amount();
        boolean applied = apply(needs, mutation.need(), current, wanted);
        if (!applied) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }

        TownsteadNeedsView after = needsOf(villager);
        double now = after == null ? current : currentOf(after, mutation.need());
        int requested = (int) Math.round(Math.abs(wanted - current));
        int landed = (int) Math.round(Math.abs(now - current));
        return now == current && requested > 0
                ? TownsteadMutationResult.noChange(current)
                : TownsteadMutationResult.success(requested, landed, current, now);
    }

    private static boolean isThirst(NeedMutation.Need need) {
        return need == NeedMutation.Need.THIRST || need == NeedMutation.Need.QUENCHED;
    }

    private static double currentOf(TownsteadNeedsView needs, NeedMutation.Need need) {
        return switch (need) {
            case HUNGER -> needs.hunger();
            case SATURATION -> needs.saturation();
            case THIRST -> needs.thirst();
            case QUENCHED -> needs.quenched();
            case FATIGUE -> needs.fatigue();
            case ENERGY -> needs.energy();
        };
    }

    /** Clamps to the range Townstead keeps the need in, then writes it. */
    private static boolean apply(Object needs, NeedMutation.Need need, double current, double wanted) {
        try {
            double bounded = NeedMutation.clamp(need, wanted);
            switch (need) {
                case HUNGER -> H_SET_HUNGER.invoke(needs, (int) Math.round(bounded));
                case SATURATION -> H_SET_SATURATION.invoke(needs, (float) bounded);
                case THIRST -> H_SET_THIRST.invoke(needs, (int) Math.round(bounded));
                case QUENCHED -> H_SET_QUENCHED.invoke(needs, (int) Math.round(bounded));
                case FATIGUE -> H_SET_FATIGUE.invoke(needs, (int) Math.round(bounded));
                case ENERGY -> {
                    int restore = (int) Math.round(bounded - current);
                    if (restore <= 0) {
                        // Losing energy is fatigue gained, and there is no "tire them out" recovery call.
                        H_SET_FATIGUE.invoke(needs,
                                (int) Math.round(TownsteadNeedsView.FATIGUE_MAX - bounded));
                    } else {
                        H_RESTORE_ENERGY.invoke(needs, restore);
                    }
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Awards profession XP, preferring the maths Townstead does for itself.
     *
     * <p>{@code ProfessionProgress.addXp} already honours the daily cap, the maximum, and the tier-up
     * bookkeeping -- but {@code ProfessionXpType} has only four constants, so it can only serve those
     * four professions. Everything else, including data-driven ones, goes through the string-keyed
     * store with the same rules applied by hand from the progression spec. Reimplementing the maths for
     * all professions would have been the easier read and the wrong answer, because the two would
     * inevitably drift.
     */
    static TownsteadMutationResult awardProfessionXp(Entity villager, String professionId, int requested,
                                                     boolean respectDailyCap, long gameTime, long worldDay) {
        if (!R.has(TownsteadCapability.AWARD_PROFESSION_XP)) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (requested <= 0) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INVALID_VALUE);
        }
        Object state = statik(H_VILLAGERS_GET, villager);
        Object memory = ref(H_PROFESSION_MEMORY, state);
        if (memory == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }

        Object xpType = respectDailyCap ? xpTypeFor(professionId) : null;
        if (xpType != null) {
            return awardThroughTownstead(memory, xpType, requested, gameTime);
        }
        return awardThroughStore(memory, professionId, requested, respectDailyCap, gameTime, worldDay);
    }

    /** The preferred path: Townstead applies its own caps and tells us what it did. */
    private static TownsteadMutationResult awardThroughTownstead(Object memory, Object xpType, int requested,
                                                                 long gameTime) {
        try {
            Object result = H_ADD_XP.invoke(memory, xpType, requested, gameTime);
            if (result == null) {
                return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
            }
            int applied = integer(H_GAIN_APPLIED, result);
            int tierBefore = integer(H_GAIN_TIER_BEFORE, result);
            int tierAfter = integer(H_GAIN_TIER_AFTER, result);
            return applied == 0
                    ? TownsteadMutationResult.failed(TownsteadMutationResult.Reason.DAILY_CAP)
                    : TownsteadMutationResult.xp(requested, applied, 0, applied, tierBefore, tierAfter);
        } catch (Throwable t) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }
    }

    /**
     * The general path, applying the spec by hand for a profession Townstead has no enum constant for.
     * Mirrors Townstead spec 4.4 step for step: reset the daily counter on a new world day, take the
     * smaller of the request, the remaining cap and the room below the maximum, recompute the tier, and
     * keep the previous tier-up tick unless the tier actually rose.
     */
    private static TownsteadMutationResult awardThroughStore(Object memory, String professionId, int requested,
                                                            boolean respectDailyCap, long gameTime,
                                                            long worldDay) {
        Object spec = statik(H_SPEC, professionId);
        Object record = ref(H_MEMORY_XP, memory, professionId);
        if (spec == null || record == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        int currentXp = integer(H_XP_XP, record);
        int currentTier = integer(H_XP_TIER, record);
        long lastTierUp = lng(H_XP_LAST_TIER_UP, record);

        // The arithmetic lives in TownsteadXpMath so it can be tested without a bound Townstead.
        TownsteadXpMath.Award award = TownsteadXpMath.award(requested, currentXp,
                integer(H_SPEC_MAX_XP, spec), integer(H_XP_TODAY, record), lng(H_XP_DAY, record),
                worldDay, integer(H_SPEC_DAILY_CAP, spec), respectDailyCap);
        if (!award.granted()) {
            return TownsteadMutationResult.failed(switch (award.outcome()) {
                case DAILY_CAP -> TownsteadMutationResult.Reason.DAILY_CAP;
                case AT_MAX, INVALID -> TownsteadMutationResult.Reason.INVALID_VALUE;
                case GRANTED -> TownsteadMutationResult.Reason.INTERNAL_ERROR;
            });
        }

        int newTier = tierForXp(spec, award.newXp(), currentTier);
        long tierUpTick = TownsteadXpMath.tierUpTick(currentTier, newTier, lastTierUp, gameTime);
        try {
            Object updated = H_XP_NEW.invoke(award.newXp(), newTier, tierUpTick, worldDay,
                    award.newXpToday());
            H_MEMORY_SET_XP.invoke(memory, professionId, updated);
        } catch (Throwable t) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }

        Object after = ref(H_MEMORY_XP, memory, professionId);
        int finalXp = after == null ? award.newXp() : integer(H_XP_XP, after);
        int finalTier = after == null ? newTier : integer(H_XP_TIER, after);
        return TownsteadMutationResult.xp(requested, award.applied(), currentXp, finalXp,
                currentTier, finalTier);
    }

    private static int tierForXp(Object spec, int xp, int fallback) {
        try {
            return (int) H_SPEC_TIER_FOR_XP.invoke(spec, xp);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** The {@code ProfessionXpType} constant whose id matches, or null when Townstead has none. */
    @Nullable
    private static Object xpTypeFor(String professionId) {
        try {
            Object values = H_XP_TYPE_VALUES.invoke();
            if (!(values instanceof Object[] constants)) {
                return null;
            }
            for (Object constant : constants) {
                String id = str(H_XP_TYPE_ID, constant);
                if (!id.isEmpty() && professionId.toLowerCase(Locale.ROOT).endsWith(id.toLowerCase(Locale.ROOT))) {
                    return constant;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the general path, which serves every profession anyway.
        }
        return null;
    }

    static Set<ResourceLocation> learnedSkills(Entity villager) {
        if (villager == null || !R.has(TownsteadCapability.MUTATE_SKILLS)) {
            return Set.of();
        }
        Object learned = statik(H_SKILLS_LEARNED, villager);
        if (!(learned instanceof Set<?> raw)) {
            return Set.of();
        }
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (Object element : raw) {
            if (element instanceof ResourceLocation id) {
                out.add(id);
            }
        }
        return Set.copyOf(out);
    }

    static boolean hasSkill(Entity villager, ResourceLocation skillId) {
        if (villager == null || skillId == null || !R.has(TownsteadCapability.MUTATE_SKILLS)) {
            return false;
        }
        try {
            return (boolean) H_SKILLS_HAS.invoke(villager, skillId);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Learning a skill already known is an idempotent success, not an error. */
    static TownsteadMutationResult learnSkill(Entity villager, ResourceLocation skillId, boolean force) {
        if (!R.has(TownsteadCapability.MUTATE_SKILLS)) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (villager == null || skillId == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        if (hasSkill(villager, skillId)) {
            return TownsteadMutationResult.noChange(1);
        }
        Object result = statik(force ? H_SKILLS_FORCE_LEARN : H_SKILLS_LEARN, villager, skillId);
        if (result == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }
        return bool(H_SKILL_OK, result)
                ? TownsteadMutationResult.success(1, 1, 0, 1)
                // Townstead refused for a reason of its own -- unmet prerequisites, usually. That is a
                // gate, not a breakage, so it reports as gated rather than as an internal error.
                : TownsteadMutationResult.failed(TownsteadMutationResult.Reason.FEATURE_GATED);
    }

    static TownsteadMutationResult forgetSkill(Entity villager, ResourceLocation skillId) {
        if (!R.has(TownsteadCapability.MUTATE_SKILLS)) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (villager == null || skillId == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        if (!hasSkill(villager, skillId)) {
            return TownsteadMutationResult.noChange(0);
        }
        Object result = statik(H_SKILLS_FORGET, villager, skillId);
        return result != null && bool(H_FORGET_OK, result)
                ? TownsteadMutationResult.success(1, 1, 1, 0)
                : TownsteadMutationResult.failed(TownsteadMutationResult.Reason.FEATURE_GATED);
    }

    /**
     * Plays a Townstead reaction for a lifecycle transition. Always called after the quest transaction
     * has committed, so the worst a failure here can do is leave a villager not waving.
     */
    static TownsteadMutationResult dispatchTransition(ServerLevel level, LivingEntity villager,
                                                      ResourceLocation taskId, String phase) {
        if (!R.has(TownsteadCapability.DISPATCH_REACTION)) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
        }
        if (level == null || villager == null || taskId == null) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.TARGET_MISSING);
        }
        try {
            int played = (int) H_REACTION.invoke(level, villager, taskId, phase);
            return played > 0
                    ? TownsteadMutationResult.success(1, played, 0, played)
                    : TownsteadMutationResult.noChange(0);
        } catch (Throwable t) {
            return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }
    }

    @Nullable
    private static Object needsState(Entity villager) {
        return ref(H_STATE_NEEDS, statik(H_VILLAGERS_GET, villager));
    }

    @Nullable
    private static TownsteadNeedsView needsOf(Entity villager) {
        return villager(villager).map(TownsteadVillagerView::needs).orElse(null);
    }

    @Nullable
    private static Object ref(MethodHandle handle, @Nullable Object receiver, Object a) {
        if (receiver == null) {
            return null;
        }
        try {
            return handle.invoke(receiver, a);
        } catch (Throwable t) {
            return null;
        }
    }

    // --- mapping helpers -------------------------------------------------------------------------

    private static TownsteadNeedsView needs(@Nullable Object snapshot) {
        return new TownsteadNeedsView(
                integer(H_N_HUNGER, snapshot),
                flt(H_N_SATURATION, snapshot),
                flt(H_N_HUNGER_EXH, snapshot),
                integer(H_N_THIRST, snapshot),
                integer(H_N_QUENCHED, snapshot),
                flt(H_N_THIRST_EXH, snapshot),
                integer(H_N_FATIGUE, snapshot),
                bool(H_N_COLLAPSED, snapshot),
                bool(H_N_GATED, snapshot));
    }

    private static TownsteadScheduleView schedule(@Nullable Object snapshot) {
        return new TownsteadScheduleView(
                str(H_S_MODE, snapshot),
                str(H_S_TEMPLATE_ID, snapshot),
                bool(H_S_CUSTOM_SHIFTS, snapshot),
                bool(H_S_NON_DEFAULT, snapshot),
                integer(H_S_TICK_HOUR, snapshot),
                integer(H_S_DISPLAY_HOUR, snapshot),
                integer(H_S_SHIFT_ORDINAL, snapshot),
                str(H_S_CURRENT_ACTIVITY, snapshot),
                str(H_S_PLANNED_ACTIVITY, snapshot),
                str(H_S_CURRENT_TEMPLATE, snapshot),
                intList(H_S_SHIFTS, snapshot),
                stringList(H_S_WEEKDAYS, snapshot));
    }

    @Nullable
    private static UUID uuid(String raw) {
        try {
            return raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --- invocation ------------------------------------------------------------------------------

    @Nullable
    private static Object statik(MethodHandle handle, Object a) {
        try {
            return handle.invoke(a);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Object statik(MethodHandle handle, Object a, Object b) {
        try {
            return handle.invoke(a, b);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Object ref(MethodHandle handle, @Nullable Object receiver) {
        if (receiver == null) {
            return null;
        }
        try {
            return handle.invoke(receiver);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String str(MethodHandle handle, @Nullable Object receiver) {
        Object value = ref(handle, receiver);
        return value instanceof String s ? s : "";
    }

    /** Lowercased {@link Enum#name()}, so no Townstead enum constant ever reaches a view record. */
    private static String enumName(@Nullable Object value) {
        return value instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT) : "";
    }

    private static int integer(MethodHandle handle, @Nullable Object receiver) {
        if (receiver == null) {
            return 0;
        }
        try {
            return (int) handle.invoke(receiver);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long lng(MethodHandle handle, @Nullable Object receiver) {
        if (receiver == null) {
            return 0L;
        }
        try {
            return (long) handle.invoke(receiver);
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static float flt(MethodHandle handle, @Nullable Object receiver) {
        if (receiver == null) {
            return 0f;
        }
        try {
            return (float) handle.invoke(receiver);
        } catch (Throwable t) {
            return 0f;
        }
    }

    private static boolean bool(MethodHandle handle, @Nullable Object receiver) {
        if (receiver == null) {
            return false;
        }
        try {
            return (boolean) handle.invoke(receiver);
        } catch (Throwable t) {
            return false;
        }
    }

    private static List<Object> list(MethodHandle handle, @Nullable Object receiver) {
        Object value = ref(handle, receiver);
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<Object> out = new ArrayList<>(raw.size());
        for (Object element : raw) {
            if (element != null) {
                out.add(element);
            }
        }
        return out;
    }

    private static List<String> stringList(MethodHandle handle, @Nullable Object receiver) {
        List<String> out = new ArrayList<>();
        for (Object element : list(handle, receiver)) {
            if (element instanceof String s) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }

    private static List<Integer> intList(MethodHandle handle, @Nullable Object receiver) {
        List<Integer> out = new ArrayList<>();
        for (Object element : list(handle, receiver)) {
            if (element instanceof Integer i) {
                out.add(i);
            }
        }
        return List.copyOf(out);
    }

    private static Map<String, String> stringMap(MethodHandle handle, @Nullable Object receiver) {
        Map<String, String> out = new LinkedHashMap<>();
        forEachEntry(handle, receiver, (key, value) -> {
            if (value instanceof String s) {
                out.put(key, s);
            }
        });
        return Map.copyOf(out);
    }

    private static Map<String, Float> floatMap(MethodHandle handle, @Nullable Object receiver) {
        Map<String, Float> out = new LinkedHashMap<>();
        forEachEntry(handle, receiver, (key, value) -> {
            if (value instanceof Number n) {
                out.put(key, n.floatValue());
            }
        });
        return Map.copyOf(out);
    }

    private static Map<String, Integer> intMap(MethodHandle handle, @Nullable Object receiver) {
        Map<String, Integer> out = new LinkedHashMap<>();
        forEachEntry(handle, receiver, (key, value) -> {
            if (value instanceof Number n) {
                out.put(key, n.intValue());
            }
        });
        return Map.copyOf(out);
    }

    /** Walks a string-keyed map from a handle, skipping anything that is not one. */
    private static void forEachEntry(MethodHandle handle, @Nullable Object receiver,
                                     java.util.function.BiConsumer<String, Object> consumer) {
        Object value = ref(handle, receiver);
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() != null) {
                consumer.accept(key, entry.getValue());
            }
        }
    }

    private TownsteadHandles() {
    }
}
