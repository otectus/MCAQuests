package dev.otectus.mcaquests.quest;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/** Where a completed quest may be turned in (spec section 17). Phase 1 implements ORIGINAL_GIVER. */
public enum TurnInMode {
    ORIGINAL_GIVER,
    SAME_PROFESSION,
    ANY_VILLAGER,
    SELF_COMPLETE,
    SPECIFIED_PROFESSION;

    public static final Codec<TurnInMode> CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown turn_in mode: " + name);
                }
            },
            mode -> DataResult.success(mode.name().toLowerCase(Locale.ROOT)));

    /**
     * Whether a quest with this {@code failure} block (or none) must fail when its giver dies.
     *
     * <p>The whole rule in one pure place, because it is asked from two very different moments: the
     * {@code LivingDeathEvent} for everyone online, and login for everyone who was not. Those two used to
     * be one expression written out once, in the event handler, which is why the offline half of the rule
     * did not exist. {@code globalFail} is the {@code failQuestIfGiverDies} config, which only applies to
     * a quest handed back to the giver — there is nobody else to hand it to.
     */
    public boolean failsOnGiverDeath(Optional<FailureSpec> failure, boolean globalFail) {
        return failure.map(FailureSpec::failOnGiverDeath).orElse(false)
                || (globalFail && this == ORIGINAL_GIVER);
    }
}
