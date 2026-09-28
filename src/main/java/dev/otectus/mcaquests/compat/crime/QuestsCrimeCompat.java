package dev.otectus.mcaquests.compat.crime;

import dev.otectus.mcacrime.api.McaCrimeApi;
import dev.otectus.mcacrime.crime.Band;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.CrimeBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import java.util.Locale;
import java.util.Optional;

/**
 * The MCA: Crime half of the bridge — the only classes in this mod that name a {@code mcacrime} type
 * live in this package (1.7.1).
 *
 * <p>Loaded by name from {@link CrimeBridge#init()} after {@code ModList} confirms the mod is installed,
 * and never referenced from anywhere else, so a server without MCA: Crime never asks a classloader for
 * any of the imports above; {@code NoCrimeStaticLinkTest} enforces that by scanning the compiled output.
 * Compiled against Crime's vendored compile-only API jar, so only its published surface can be named.
 *
 * <p>Every query fails safe: an exception is a "no" (not wanted, not jailed), never a crash in an offer
 * pass or a progress tick.
 */
public final class QuestsCrimeCompat implements CrimeBridge.CrimeQueries {

    private QuestsCrimeCompat() {
    }

    /** Sole entry point from {@link CrimeBridge#init()}: installs the query facade and the event subscriber. */
    public static void register() {
        // The version handshake: MCA: Crime publishes no API generation constant, so the honest test is
        // whether these calls resolve. A NoSuchMethodError or NoClassDefFoundError out of here is what
        // CrimeBridge catches and reports as an incompatible build.
        McaCrimeApi.class.getName();
        MinecraftForge.EVENT_BUS.register(new QuestsCrimeEvents());
        CrimeBridge.setQueries(new QuestsCrimeCompat());
        McaQuests.LOGGER.info("[MCA: Quests] MCA: Crime integration: registered the wanted/custody queries "
                + "and the jail event subscriber.");
    }

    @Override
    public boolean isWanted(ServerPlayer player) {
        try {
            return McaCrimeApi.isWanted(player);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Crime isWanted failed; defaulting false", t);
            return false;
        }
    }

    @Override
    public Optional<String> band(ServerPlayer player) {
        try {
            Band band = McaCrimeApi.getBand(player);
            if (band == null) {
                return Optional.empty();
            }
            // Crime's internal names are colours; players and packs see the legal words.
            return Optional.of(switch (band.name().toUpperCase(Locale.ROOT)) {
                case "BLUE" -> "lawful";
                case "GREY" -> "neutral";
                case "RED" -> "outlaw";
                default -> band.name().toLowerCase(Locale.ROOT);
            });
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Crime getBand failed; defaulting empty", t);
            return Optional.empty();
        }
    }

    @Override
    public long heat(ServerPlayer player) {
        try {
            return McaCrimeApi.getHeat(player);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Crime getHeat failed; defaulting 0", t);
            return 0L;
        }
    }

    @Override
    public boolean isJailed(ServerPlayer player) {
        try {
            // The event subscriber is the fast path; the sentence read covers a player jailed before this
            // server session started, when no event was seen.
            return QuestsCrimeEvents.isJailed(player.getUUID()) || McaCrimeApi.sentence(player).isPresent();
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Crime sentence read failed; defaulting false", t);
            return false;
        }
    }
}
