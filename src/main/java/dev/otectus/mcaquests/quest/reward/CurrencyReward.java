package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.QuestDifficulty;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * A <em>semantic</em> money reward: "pay the player some currency", leaving the server to decide what
 * currency means (spec 1.1.0). Vanilla emeralds by default; a server can switch every currency reward in
 * every pack to Create: Numismatics coins or a custom item with one config line, without any datapack
 * being rewritten — see {@link CurrencyProvider}.
 *
 * <pre>
 * { "type": "mcaquests:currency" }                          // range from the quest's difficulty band
 * { "type": "mcaquests:currency", "difficulty": "hard" }    // range from an explicit band
 * { "type": "mcaquests:currency", "min": 4, "max": 9 }      // explicit range, ignores the bands
 * </pre>
 *
 * <p><b>The amount is rolled exactly once</b>, when the quest is accepted, and stored on the
 * {@code ActiveQuest} (or, for a project phase, on the {@code ProjectState}). Re-opening the menu,
 * reconnecting, reloading the world, or a retried turn-in packet all read that stored number back — there
 * is no path that rolls again, so a player can never reroll a payout by reopening the UI. Scaling and
 * clamping happen at roll time too, so the number shown on an accepted quest is exactly the number paid.
 */
public record CurrencyReward(Optional<Integer> min, Optional<Integer> max,
                             Optional<QuestDifficulty> difficulty) implements QuestReward {

    public static final Codec<CurrencyReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("min").forGetter(CurrencyReward::min),
            Codec.INT.lenientOptionalFieldOf("max").forGetter(CurrencyReward::max),
            QuestDifficulty.CODEC.lenientOptionalFieldOf("difficulty").forGetter(CurrencyReward::difficulty)
    ).apply(instance, CurrencyReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.CURRENCY;
    }

    /** The offer-card summary: an honest range, or a single number when the range has no spread. */
    @Override
    public Component describe() {
        int low = effectiveMin();
        int high = effectiveMax();
        Component name = currencyName();
        return low == high
                ? Component.translatable("mcaquests.reward.currency", name, low)
                : Component.translatable("mcaquests.reward.currency_range", name, low, high);
    }

    /** The accepted-quest summary: the exact frozen amount this player will be paid. */
    public Component describeFrozen(int amount) {
        return Component.translatable("mcaquests.reward.currency", currencyName(), amount);
    }

    /**
     * Rolls this reward's payout for one acceptance, already scaled by {@code currencyRewardMultiplier}
     * and clamped to at least 0. Call once, at accept time, and persist the result.
     */
    public int roll(RandomSource random) {
        // Roll on the RAW range and scale once. Rolling on the already-scaled range and scaling again
        // would apply the multiplier twice, paying 4x at a multiplier of 2.
        int low = rawMin();
        int high = Math.max(low, rawMax());
        int rolled = low >= high ? low : low + random.nextInt(high - low + 1);
        return scale(rolled);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Granting without a frozen amount should not happen — every path that accepts a quest freezes one
     * first — so this rolls a fresh amount only as a last-resort safety net (e.g. a reward added to a
     * quest a player had already accepted) and says so in the log.
     */
    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        McaQuests.LOGGER.debug("[MCA: Quests] Currency reward granted with no frozen amount; rolling now.");
        grantAmount(player, roll(player.getRandom()));
    }

    /** Pays exactly {@code amount}, splitting across stacks so a large payout is not silently truncated. */
    public void grantAmount(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        Item item = CurrencyProvider.currencyItem().orElse(null);
        if (item == null) {
            return; // provider unavailable and currencyFallback = DISABLE; already logged once
        }
        int remaining = amount;
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item, give));
            remaining -= give;
        }
    }

    /** The band's (or the explicit) low end, after scaling — the number shown to the player. */
    public int effectiveMin() {
        return scale(rawMin());
    }

    /** The band's (or the explicit) high end, after scaling — the number shown to the player. */
    public int effectiveMax() {
        return scale(Math.max(rawMin(), rawMax()));
    }

    /** The unscaled low end: the explicit {@code min} if given, else the difficulty band's. */
    private int rawMin() {
        return min.orElseGet(() -> bandMin(band()));
    }

    /**
     * The unscaled high end. Clamped up to {@link #rawMin()} so a pack that writes {@code max < min}
     * (reported as a load error, but still loadable) degrades to a fixed payout instead of making
     * {@code roll} call {@code nextInt} with a non-positive bound.
     */
    private int rawMax() {
        return Math.max(rawMin(), max.orElseGet(() -> bandMax(band())));
    }

    private QuestDifficulty band() {
        return difficulty.orElse(QuestDifficulty.DEFAULT);
    }

    private static int scale(int amount) {
        double scaled = amount * McaQuestsConfig.COMMON.currencyRewardMultiplier.get();
        return Math.max(0, (int) Math.round(scaled));
    }

    private static int bandMin(QuestDifficulty band) {
        return switch (band) {
            case EASY -> McaQuestsConfig.COMMON.easyCurrencyMin.get();
            case MEDIUM -> McaQuestsConfig.COMMON.mediumCurrencyMin.get();
            case HARD -> McaQuestsConfig.COMMON.hardCurrencyMin.get();
        };
    }

    private static int bandMax(QuestDifficulty band) {
        return switch (band) {
            case EASY -> McaQuestsConfig.COMMON.easyCurrencyMax.get();
            case MEDIUM -> McaQuestsConfig.COMMON.mediumCurrencyMax.get();
            case HARD -> McaQuestsConfig.COMMON.hardCurrencyMax.get();
        };
    }

    private static Component currencyName() {
        return CurrencyProvider.currencyItem()
                .map(item -> (Component) item.getDescription())
                .orElseGet(() -> Component.literal(CurrencyProvider.configuredId()));
    }

    /** Cross-field validation surfaced by the loader: a reversed or negative range is an authoring error. */
    public void validate(String prefix, List<String> errors) {
        if (min.isPresent() && min.get() < 0) {
            errors.add(prefix + " has a negative currency 'min' (" + min.get() + ").");
        }
        if (max.isPresent() && max.get() < 0) {
            errors.add(prefix + " has a negative currency 'max' (" + max.get() + ").");
        }
        if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
            errors.add(prefix + " has currency 'min' (" + min.get() + ") greater than 'max' (" + max.get() + ").");
        }
    }
}
