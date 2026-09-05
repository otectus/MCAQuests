package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.compat.CompatProvider;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * Bountiful, as MCA: Quests is allowed to see it.
 *
 * <p>Three implementations exist and exactly one is in use at a time: nothing installed or switched
 * off ({@code NoopBountifulBridge}), installed and readable but with no completion hook
 * ({@code DataOnlyBountifulBridge}), and installed with the cash-in hook available
 * ({@code HookedBountifulBridge}). Which one is chosen is re-decided on every re-probe by
 * {@link BountifulCompat}, so a config change or a different mod set is answered on the next
 * {@code /reload} rather than at the next restart.
 *
 * <p>Splitting the integration into whole bridges rather than branching on booleans inside one class
 * is what keeps the "not installed" path genuinely empty: the Noop bridge has no handles, no
 * listeners and no code that could touch Bountiful, so there is nothing to go wrong on the install
 * that is by far the most common.
 *
 * <p><b>No Bountiful type appears in this interface or in any implementation.</b> Everything crosses
 * as vanilla types, {@link BountySnapshot}s and strings; {@code NoBountifulStaticLinkTest} enforces
 * it.
 */
public interface BountifulBridge extends CompatProvider {

    /** The mod id, and this provider's id in the compat registry. */
    String MOD_ID = "bountiful";

    /**
     * One independently-answerable thing this bridge can or cannot do.
     *
     * <p>These are the ids datapacks gate on through {@code mcaquests:compat_capability}, so their
     * names are part of the pack contract.
     */
    enum Capability {

        /** Our conditional Bountiful pools and decrees can be mounted for Bountiful's loader. */
        DATA_PACK,

        /** {@code bountiful:bountyboard} is a registered block, so a board can actually be found. */
        BOARD_REGISTRY,

        /** A successful cash-in is observable, so bounty-completion objectives can advance. */
        CASH_IN_HOOK,

        /** A bounty's rarity can be read, so {@code min_rarity} means something. */
        READ_RARITY,

        /** A bounty's objective list can be read. */
        READ_OBJECTIVES;

        /** The dotted id used in datapacks and in {@code /mcaquests compat status}. */
        public String id() {
            return MOD_ID + "." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Whether this bridge can do {@code capability} right now. */
    boolean has(Capability capability);

    /**
     * What can be read off a bounty item, or empty when {@code stack} is not one — or when nothing
     * can be read at all, which is the Noop bridge's permanent answer.
     */
    Optional<BountySnapshot> inspect(ItemStack stack);

    /**
     * Registers a listener for successful cash-ins.
     *
     * <p>Accepted by every bridge and honoured only by the hooked one. A listener registered against
     * a bridge that cannot observe cash-ins is simply never called, which is the same thing that
     * happens when nobody ever cashes a bounty in — so no caller has to check first, and no caller
     * gets a different code path on an installation without the mod.
     */
    void addCompletionListener(BountifulCompletionListener listener);
}
