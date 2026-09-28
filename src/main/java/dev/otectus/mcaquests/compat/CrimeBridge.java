package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The optional-classloading seam for MCA: Crime (1.7.1), built to exactly the discipline
 * {@link ReputationBridge} uses.
 *
 * <p><b>Nothing in this file, or in anything it can reach without the mod-present check, may name a
 * {@code mcacrime} type.</b> The implementation lives entirely under {@code compat.crime}, is compiled
 * against Crime's vendored compile-only API jar, and is reached only by the string below after
 * {@link ModList} confirms the mod is present. Everything on this side of the seam speaks the pure
 * {@link CrimeQueries} SPI, in Minecraft and Java types only.
 *
 * <h2>What the integration does</h2>
 *
 * <ul>
 *   <li>A guard or archer refuses quest business — offers, acceptance, turn-ins — with a player MCA:
 *       Crime says is <em>wanted</em> ({@code compat.crime.responderRefusesWanted}); every giver does
 *       under {@code compat.crime.allGiversRefuseWanted}. Nothing is failed or taken away: the quests
 *       the player already holds keep running, they simply cannot be handed in to the law until the
 *       warrant is settled.</li>
 *   <li>Active quests pause while the player is jailed ({@code compat.crime.pauseWhileJailed}), the same
 *       way they pause for a missing optional mod, so a sentence cannot expire a deadline.</li>
 *   <li>The {@code mcaquests:crime_status} condition lets a datapack gate an offer on wanted status,
 *       band, custody or heat.</li>
 * </ul>
 *
 * <p>Without MCA: Crime, or with the integration off, every query answers "not wanted, not jailed" and
 * the condition is unmet — never a crash, never an offer that depends on a fact nobody can supply.
 */
public final class CrimeBridge {

    /** Everything Quests asks of MCA: Crime, in Minecraft and Java types. Every method fails safe. */
    public interface CrimeQueries {
        /** Whether MCA: Crime currently holds a warrant on this player. */
        boolean isWanted(ServerPlayer player);

        /** The player's band as a lowercase token: {@code lawful}, {@code neutral} or {@code outlaw}. */
        Optional<String> band(ServerPlayer player);

        /** The player's current Heat. */
        long heat(ServerPlayer player);

        /** Whether the player is serving a jail sentence right now. */
        boolean isJailed(ServerPlayer player);
    }

    private static final String MOD_ID = "mcacrime";

    private static volatile CrimeQueries queries;
    private static volatile boolean initialised;
    private static volatile String status = "not initialised";

    private CrimeBridge() {
    }

    /** Installs the query facade. Called by {@code QuestsCrimeCompat.register()} after the presence check. */
    public static void setQueries(@Nullable CrimeQueries implementation) {
        queries = implementation;
    }

    /** Chooses whether the integration runs. Called once from common setup, after every mod has loaded. */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            status = "not installed";
            McaQuests.LOGGER.info("[MCA: Quests] MCA: Crime is not installed; wanted players are not refused "
                    + "and the crime_status condition is never met.");
            return;
        }
        try {
            // Class.forName rather than a direct reference: naming the adapter here would put its whole
            // constant pool -- MCA: Crime types included -- behind a class that loads always.
            Class.forName("dev.otectus.mcaquests.compat.crime.QuestsCrimeCompat").getMethod("register").invoke(null);
            if (queries == null) {
                status = "adapter did not install";
                McaQuests.LOGGER.error("[MCA: Quests] the MCA: Crime adapter loaded but installed nothing; "
                        + "the integration is off.");
                return;
            }
            status = "ready";
            McaQuests.LOGGER.info("[MCA: Quests] MCA: Crime detected; guards refuse quest business with wanted "
                    + "players and active quests pause while a player is jailed.");
        } catch (Throwable t) {
            queries = null;
            status = "incompatible MCA: Crime (" + t.getClass().getSimpleName() + ")";
            McaQuests.LOGGER.error("[MCA: Quests] MCA: Crime is installed but the integration could not start; "
                    + "quests behave as though it were absent.", t);
        }
    }

    /** The live query facade, or empty when the integration is off for any reason. */
    public static Optional<CrimeQueries> queries() {
        return Optional.ofNullable(queries);
    }

    /** True once MCA: Crime is confirmed present and the adapter registered. */
    public static boolean isAvailable() {
        return queries != null;
    }

    /** A short human-readable state for the compat status command. */
    public static String status() {
        return status;
    }

    /**
     * Whether this villager refuses quest business with this player right now: offers, acceptance and
     * turn-ins alike. The decision itself is {@link #refuses(boolean, boolean, boolean, boolean)}.
     */
    public static boolean refusesService(ServerPlayer player, @Nullable Entity villager) {
        CrimeQueries live = queries;
        if (live == null || player == null || villager == null) {
            return false;
        }
        try {
            boolean wanted = live.isWanted(player);
            if (!wanted) {
                return false;
            }
            boolean responder = McaCompat.getProfessionId(villager)
                    .map(id -> "guard".equals(id.getPath()) || "archer".equals(id.getPath()))
                    .orElse(false);
            return refuses(true, responder,
                    McaQuestsConfig.COMMON.crimeResponderRefusesWanted.get(),
                    McaQuestsConfig.COMMON.crimeAllGiversRefuseWanted.get());
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] MCA: Crime wanted check failed; not refusing", t);
            return false;
        }
    }

    /**
     * The pure refusal rule: only a wanted player is ever refused; the law (guards and archers) refuses
     * under {@code responderRefusesWanted}, everybody under {@code allGiversRefuseWanted}.
     */
    static boolean refuses(boolean wanted, boolean responder, boolean responderRefusesWanted,
                           boolean allGiversRefuseWanted) {
        if (!wanted) {
            return false;
        }
        return allGiversRefuseWanted || (responder && responderRefusesWanted);
    }

    /** Whether this player's active quests should accrue suspended time right now: jailed, and the option on. */
    public static boolean pausesQuests(ServerPlayer player) {
        CrimeQueries live = queries;
        if (live == null || player == null) {
            return false;
        }
        try {
            return McaQuestsConfig.COMMON.crimePauseWhileJailed.get() && live.isJailed(player);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] MCA: Crime custody check failed; not pausing", t);
            return false;
        }
    }

    /** The refusal line's translation key, for the menu card. */
    public static final String REFUSED_KEY = "mcaquests.status.crime_refused";

    /** The id under which this integration reports to the compat registry. */
    public static final ResourceLocation PROVIDER_ID = new ResourceLocation(MOD_ID, "compat");

    /** Test seam. */
    public static synchronized void resetForTest() {
        queries = null;
        initialised = false;
        status = "not initialised";
    }
}
