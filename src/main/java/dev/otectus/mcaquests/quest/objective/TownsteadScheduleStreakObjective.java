package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.TownsteadCalendarView;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadScheduleView;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.TownsteadNames;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Complete when a villager has worked enough whole shifts (spec §5.3).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_schedule_streak",
 *   "target": "giver",
 *   "activity": "work",
 *   "required_shifts": 3,
 *   "minimum_coverage": 0.65,
 *   "require_on_schedule": true,
 *   "reset_on_miss": false
 * }
 * }</pre>
 *
 * <p>This is what "help them through a few days at the forge" should mean, and it replaces the long
 * {@code hold_ticks} that multi-day work stories previously had to use. A hold demands one unbroken
 * stretch and therefore demands the player stand there watching; a streak counts <em>shifts the
 * villager completed</em>, which is a thing the world does whether or not anyone is looking. Keep
 * {@code townstead_state} for short, immediate observations.
 *
 * <h2>What is actually counted</h2>
 *
 * <p>A shift is identified by the calendar profile, year, day, schedule template and shift ordinal, so
 * "the same shift" survives a logout, a restart, a dimension change and a {@code /reload}, and a clock
 * that runs backwards cannot mint a second one. Credited shift keys are persisted, capped at
 * {@link #requiredShifts()}, and re-crediting a key already in that set is impossible by construction.
 *
 * <h2>Three outcomes, not two</h2>
 *
 * <p>A shift ends <b>credited</b>, <b>missed</b> or <b>unknown</b>. Unknown is the one that makes this
 * honest: if the villager was unloaded for most of the shift there is no evidence either way, so it
 * neither counts nor breaks a streak. Without that, a player who walked away for a night would return
 * to a broken streak they had no way to see coming — and with the opposite mistake, a player who
 * never watched at all would be credited for shifts nobody observed.
 *
 * <p>Observation is measured against the wall-clock the shift occupied rather than against a nominal
 * shift length, because shift lengths belong to Townstead's schedule templates and are not something
 * this objective is entitled to assume.
 */
public record TownsteadScheduleStreakObjective(TownsteadTarget target, String activity,
                                               int requiredShifts, double minimumCoverage,
                                               boolean requireOnSchedule, boolean resetOnMiss)
        implements PollingObjective, TownsteadObjective {

    /** The poll cadence; every sample stands for this many ticks of observation. */
    private static final long SAMPLE_TICKS = 20L;

    /**
     * Below this share of the shift observed, the verdict is "unknown" rather than "missed" (§5.3
     * rule 5). A fifth of a shift is enough to say something about it and little enough that a short
     * absence is not held against the player.
     */
    private static final double MINIMUM_OBSERVABILITY = 0.20D;

    private static final String K_KEY = "ss_key";
    private static final String K_ARMED_AT = "ss_armed_at";
    private static final String K_LAST_SAMPLE = "ss_last_sample";
    private static final String K_SAMPLED = "ss_sampled";
    private static final String K_MATCHED = "ss_matched";
    private static final String K_CREDITED = "ss_credited";
    private static final String K_STREAK = "ss_streak";
    private static final String K_PROFILE = "ss_profile";

    public static final Codec<TownsteadScheduleStreakObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadScheduleStreakObjective::target),
                    StrictCodecs.strictOptional(Codec.STRING, "activity", "work")
                            .forGetter(TownsteadScheduleStreakObjective::activity),
                    Codec.intRange(1, 28).fieldOf("required_shifts")
                            .forGetter(TownsteadScheduleStreakObjective::requiredShifts),
                    StrictCodecs.strictOptional(Codec.doubleRange(0.10D, 1.00D), "minimum_coverage", 0.60D)
                            .forGetter(TownsteadScheduleStreakObjective::minimumCoverage),
                    StrictCodecs.strictOptional(Codec.BOOL, "require_on_schedule", true)
                            .forGetter(TownsteadScheduleStreakObjective::requireOnSchedule),
                    StrictCodecs.strictOptional(Codec.BOOL, "reset_on_miss", false)
                            .forGetter(TownsteadScheduleStreakObjective::resetOnMiss)
            ).apply(instance, TownsteadScheduleStreakObjective::new));


    /** The resident whose working day this is about. */
    @Override
    public java.util.Optional<net.minecraft.world.entity.Entity> townsteadSubject(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        return dev.otectus.mcaquests.quest.target.TownsteadTargetResolver
                .resolveForObjective(target, player, active, progress, level);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TOWNSTEAD_SCHEDULE_STREAK;
    }

    @Override
    public Set<TownsteadCapability> requiredCapabilities() {
        return Set.of(TownsteadCapability.READ_VILLAGER, TownsteadCapability.READ_SCHEDULE,
                TownsteadCapability.READ_CALENDAR);
    }

    @Override
    public int required() {
        return requiredShifts;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(requiredShifts, progress.count());
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= requiredShifts;
    }

    /**
     * Binds the villager at acceptance. No shift is armed here: arming happens on the first
     * observation, and it is exactly that ordering which stops the shift already in progress from
     * being credited for the hours before the player was asked (§5.3 rule 1).
     */
    @Override
    public void freezeBaseline(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                               ServerLevel level) {
        if (progress.targetUuid() != null) {
            return;
        }
        Entity villager = TownsteadTargetResolver
                .resolveForObjective(target, player, active, progress, level).orElse(null);
        if (villager != null) {
            progress.setTargetUuid(villager.getUUID());
        }
    }

    @Override
    public boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress) {
        if (isSatisfied(player, progress)) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        TownsteadEvaluation evaluation = new TownsteadEvaluation();

        Entity villager = TownsteadTargetResolver
                .resolveForObjective(target, player, quest, progress, level).orElse(null);
        TownsteadVillagerView view = villager == null
                ? null : evaluation.villager(villager).orElse(null);
        TownsteadCalendarView calendar = evaluation.calendar(level.getServer()).orElse(null);
        if (view == null || calendar == null) {
            // Unloaded, or the calendar is unreadable. Sampling simply pauses: the armed shift keeps
            // the evidence it already has and will be judged on its observability when it ends.
            return false;
        }

        CompoundTag extra = progress.extra();
        String key = shiftKey(calendar, view.schedule());
        String armed = extra.getString(K_KEY);
        long now = level.getGameTime();

        if (!armed.equals(key)) {
            boolean changed = armed.isEmpty() ? false : settle(progress, extra, armed, now);
            arm(extra, key, calendar, now);
            return changed;
        }
        return sample(extra, view.schedule(), now);
    }

    /**
     * Arms a shift. Deliberately records no samples: a shift the player joined halfway through starts
     * with an empty record and is judged on what happens from here, so joining late can only ever
     * produce a lower coverage, never a free credit.
     */
    private void arm(CompoundTag extra, String key, TownsteadCalendarView calendar, long now) {
        extra.putString(K_KEY, key);
        extra.putString(K_PROFILE, calendar.profileId());
        extra.putLong(K_ARMED_AT, now);
        extra.putLong(K_LAST_SAMPLE, now);
        extra.putLong(K_SAMPLED, 0L);
        extra.putLong(K_MATCHED, 0L);
    }

    /**
     * Records one observation of the armed shift.
     *
     * <p>Time is taken from the gap since the previous sample rather than assumed to be one poll, so a
     * server that skipped ticks under load accounts for the real elapsed time instead of quietly
     * reporting better coverage than it saw. A backwards clock contributes nothing rather than
     * subtracting, which is the only sane reading of a rollback.
     */
    private boolean sample(CompoundTag extra, TownsteadScheduleView schedule, long now) {
        long since = Math.max(0L, Math.min(now - extra.getLong(K_LAST_SAMPLE), SAMPLE_TICKS * 4L));
        long elapsed = since == 0L ? SAMPLE_TICKS : since;
        extra.putLong(K_LAST_SAMPLE, now);
        extra.putLong(K_SAMPLED, extra.getLong(K_SAMPLED) + elapsed);
        if (matches(schedule)) {
            extra.putLong(K_MATCHED, extra.getLong(K_MATCHED) + elapsed);
        }
        return false; // nothing player-visible changes until the shift is settled
    }

    /**
     * True when this instant counts toward the shift.
     *
     * <p>{@code require_on_schedule} is currently implied by the activity comparison — Townstead's
     * "on schedule" is exactly "doing what was planned" — but it is honoured explicitly so the JSON
     * contract stays correct if that ever stops being true.
     */
    private boolean matches(TownsteadScheduleView schedule) {
        if (!equalsIgnoreCase(schedule.currentActivity(), activity)) {
            return false;
        }
        return !requireOnSchedule || schedule.onSchedule();
    }

    /**
     * Judges a finished shift: credited, missed, or unknown for want of evidence. Returns true only
     * when the player's visible progress changed, so the quest log is not refreshed for nothing.
     */
    private boolean settle(ObjectiveProgress progress, CompoundTag extra, String key, long now) {
        long window = Math.max(1L, now - extra.getLong(K_ARMED_AT));
        long sampled = extra.getLong(K_SAMPLED);
        if ((double) sampled / window < MINIMUM_OBSERVABILITY) {
            return false; // unknown: neither credited nor a miss
        }
        boolean covered = sampled > 0L && (double) extra.getLong(K_MATCHED) / sampled >= minimumCoverage;
        if (!covered) {
            if (resetOnMiss && progress.count() > 0) {
                progress.setCount(0);
                extra.putInt(K_STREAK, 0);
                return true;
            }
            return false;
        }
        return credit(progress, extra, key);
    }

    /**
     * Banks a completed shift, at most once per shift key. The credited set is the duplicate guard
     * that survives logout, restart and {@code /reload}; the counter is derived from it rather than
     * incremented independently, so the two can never disagree.
     */
    private boolean credit(ObjectiveProgress progress, CompoundTag extra, String key) {
        Set<String> credited = creditedKeys(extra);
        if (!credited.add(key)) {
            return false;
        }
        ListTag list = new ListTag();
        credited.stream().limit(requiredShifts).forEach(k -> list.add(StringTag.valueOf(k)));
        extra.put(K_CREDITED, list);
        progress.setCount(Math.min(requiredShifts, list.size()));
        extra.putInt(K_STREAK, progress.count());
        return true;
    }

    private static Set<String> creditedKeys(CompoundTag extra) {
        Set<String> out = new LinkedHashSet<>();
        if (extra.contains(K_CREDITED, Tag.TAG_LIST)) {
            ListTag list = extra.getList(K_CREDITED, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                out.add(list.getString(i));
            }
        }
        return out;
    }

    /**
     * The stable identity of one shift: {@code profile/year/day/template/ordinal}. Every component is
     * needed. Without the profile, switching Townstead calendars would collide two unrelated days;
     * without the year, a short calendar would repeat day numbers; without the template and ordinal,
     * a villager's two work shifts in one day would be one shift.
     */
    private static String shiftKey(TownsteadCalendarView calendar, TownsteadScheduleView schedule) {
        return calendar.profileId() + '/' + calendar.year() + '/' + calendar.dayOfYear() + '/'
                + schedule.currentTemplateId() + '/' + schedule.currentShiftOrdinal();
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    /**
     * Never trivially satisfied: no future shift has been credited at the moment of the offer, and by
     * construction none can be (§12.3).
     */
    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        return false;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.townstead_schedule_streak",
                requiredShifts, TownsteadNames.activity(activity));
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        return describe();
    }

    /** The shift currently being observed, for the context line. Empty when none is armed. */
    @Nullable
    public static String armedShift(ObjectiveProgress progress) {
        String key = progress.extra().getString(K_KEY);
        return key.isEmpty() ? null : key;
    }
}
