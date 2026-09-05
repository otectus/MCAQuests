package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;
import java.util.Optional;

/**
 * The two static methods the cash-in mixin calls, and everything that has to happen between them.
 *
 * <p>They live here rather than in the mixin because a mixin class is compiled into somebody else's
 * class and is the worst possible place to keep logic: it cannot be tested, it cannot be read in a
 * stack trace, and anything it touches is loaded on the transformer's terms. The mixin is therefore
 * two lines long and this class does the work.
 *
 * <p><b>Why two entry points.</b> A cash-in consumes the bounty item, so by the time the method
 * returns there is nothing left to read a rarity off. {@link #beforeCashIn} takes the snapshot while
 * the stack is still whole and {@link #afterCashIn} decides whether it counted — because only the
 * return value says whether Bountiful accepted it, and an incomplete or expired bounty must credit
 * nothing.
 *
 * <p><b>Nothing here may throw.</b> Every one of these methods runs inside another mod's method, on
 * the server thread, in the middle of a player's interaction. An exception would not merely lose a
 * quest credit; it would take the cash-in — and the player's bounty — down with it.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class BountifulHookEvents {

    /**
     * What was read off the bounty before it was consumed.
     *
     * @param rarity         the rank, or {@link BountyRarity#UNKNOWN} when the reader is not bound
     * @param objectiveCount how many objectives the bounty listed, or {@code 0}
     * @param dedupeKey      identifies this bounty item, so one cash-in reported twice counts once
     */
    private record Pending(BountyRarity rarity, int objectiveCount, String dedupeKey) {
    }

    /**
     * A thread-local slot rather than a field, because {@code tryCashIn} is re-entrant in principle
     * and a single field would be a cross-thread race on a dedicated server the moment anything but
     * the main thread called it. Cleared unconditionally in {@link #afterCashIn}, including on the
     * path where nothing is credited, so a failed cash-in cannot leave a snapshot behind for the next
     * one to pick up.
     */
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    /** The server game time the dedupe cache reasons about; see {@link #DEDUPE}. */
    private static volatile long gameTime;

    /**
     * Collapses one player action reported several times into one credit. Its clock is this class's
     * own view of the server tick rather than a call into the server, so the cache stays a plain
     * testable object with no Minecraft in it.
     */
    private static final CompletionDedupe DEDUPE = new CompletionDedupe(() -> gameTime);

    private BountifulHookEvents() {
    }

    /**
     * Called at the head of {@code BountyData.tryCashIn}, while the bounty item still exists.
     *
     * <p>{@code bountyData} arrives as {@link Object} and is only ever handed back to
     * {@code BountifulBinding}'s erased handles — naming its type here is exactly what this
     * integration is built not to do.
     */
    public static void beforeCashIn(Object bountyData, Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        try {
            BountifulBridge bridge = BountifulCompat.bridge();
            Optional<BountySnapshot> snapshot = bridge.inspect(stack);
            int objectives = bridge instanceof DataOnlyBountifulBridge dataBridge
                    ? dataBridge.objectiveCountOf(bountyData)
                    : 0;
            PENDING.set(new Pending(snapshot.map(BountySnapshot::rarity).orElse(BountyRarity.UNKNOWN),
                    objectives, dedupeKey(stack)));
        } catch (Throwable t) {
            PENDING.remove();
            McaQuests.LOGGER.debug("[MCA: Quests] Could not read a Bountiful bounty before cash-in; "
                    + "this cash-in will not be credited.", t);
        }
    }

    /**
     * Called at every return of {@code BountyData.tryCashIn}. Credits only a {@code true} return.
     *
     * <p>A {@code false} return is Bountiful refusing the bounty — incomplete, expired, the wrong
     * board — and crediting it would let a player advance a quest by clicking a bounty they cannot
     * finish, which is the exact bug the return value exists to prevent.
     */
    public static void afterCashIn(Object bountyData, Player player, ItemStack stack, boolean result) {
        Pending pending = PENDING.get();
        PENDING.remove();
        if (!result || pending == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        try {
            gameTime = serverPlayer.level() instanceof ServerLevel level ? level.getGameTime() : gameTime;
            BountyCompletion completion = new BountyCompletion(serverPlayer.getUUID(), gameTime,
                    pending.rarity() == BountyRarity.UNKNOWN ? "" : pending.rarity().name(),
                    pending.objectiveCount(), pending.dedupeKey());
            if (DEDUPE.accept(serverPlayer.getUUID() + "@" + pending.dedupeKey() + "@" + gameTime)) {
                BountifulCompat.dispatchCompletion(serverPlayer, completion);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.warn("[MCA: Quests] A Bountiful cash-in could not be credited; the "
                    + "cash-in itself is unaffected.", t);
        }
    }

    /**
     * Ages the dedupe cache out. {@code END} because the phase is arbitrary and the end of the tick is
     * where the tick's own cash-ins have already been recorded.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        gameTime = event.getServer().overworld().getGameTime();
        DEDUPE.sweep(gameTime);
    }

    /**
     * Identifies one bounty item well enough to notice the same one twice in a tick.
     *
     * <p>The item plus its NBT, hashed: a bounty's whole identity is its tag, and two different
     * bounties on the same tick differ in it. A hash rather than the tag itself because this is only
     * ever compared for equality within a two-tick window, and holding another mod's NBT past the
     * method that owned it is the thing this integration keeps not doing.
     */
    private static String dedupeKey(ItemStack stack) {
        return Integer.toHexString(Objects.hash(stack.getItem(), String.valueOf(stack.getTag())));
    }
}
