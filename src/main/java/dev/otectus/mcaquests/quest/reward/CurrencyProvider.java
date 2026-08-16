package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.CurrencyFallback;
import dev.otectus.mcaquests.McaQuestsConfig.CurrencyProviderMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Resolves the item that {@link CurrencyReward} pays out in, per the server's {@code currencyProvider}.
 *
 * <p>The Create: Numismatics provider is deliberately <b>id-based</b>: the coin is looked up in the item
 * registry by {@link ResourceLocation}, so MCA: Quests contains no reference to any Numismatics class,
 * needs no compile-time dependency on it, and cannot trigger a classload when the mod is absent. An
 * uninstalled Numismatics is therefore indistinguishable from a typo'd id — both are just "no such item"
 * — and both take the same {@code currencyFallback} path.
 *
 * <p>Resolution failures are reported <b>once per offending id</b> rather than once per turn-in, so a
 * misconfigured server logs a single actionable line instead of flooding the log during play.
 */
public final class CurrencyProvider {

    /** Ids already reported as unresolvable, so the warning is emitted once per id for the session. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CurrencyProvider() {
    }

    /** Vanilla's emerald id, used both as the VANILLA provider and as the universal fallback. */
    public static final String EMERALD_ID = "minecraft:emerald";

    /**
     * The item to pay out, or {@code empty} when the configured provider is unavailable and
     * {@code currencyFallback} is {@code DISABLE}.
     */
    public static Optional<Item> currencyItem() {
        return resolveCurrencyId(id -> registryLookup(id).isPresent()).flatMap(CurrencyProvider::registryLookup);
    }

    /**
     * Decides <em>which item id</em> to pay in, given a predicate that says whether an id exists.
     *
     * <p>Deliberately expressed over ids rather than {@code Item}s: that keeps the whole provider /
     * fallback / warn-once decision free of the item registry, so it is unit-testable (these tests never
     * bootstrap vanilla registries — even constructing an {@code Item} fails without them) and so the
     * <b>only</b> thing that ever touches the registry is a single {@code get} by id. Nothing here
     * references a Numismatics class, which is what makes an absent Numismatics a plain "no such id"
     * rather than a classload failure.
     *
     * @return the id to pay in, or {@code empty} when the provider is unresolvable and the configured
     *         fallback is {@code DISABLE}.
     */
    public static Optional<String> resolveCurrencyId(Predicate<String> exists) {
        String configured = configuredId();
        if (exists.test(configured)) {
            return Optional.of(configured);
        }
        warnOnce(configured, McaQuestsConfig.COMMON.currencyProvider.get());
        if (McaQuestsConfig.COMMON.currencyFallback.get() != CurrencyFallback.EMERALDS) {
            return Optional.empty();
        }
        return exists.test(EMERALD_ID) ? Optional.of(EMERALD_ID) : Optional.empty();
    }

    /**
     * The configured item id, purely for display and diagnostics. Never touches the registry, so it is
     * safe to call on a client that has a different set of mods than the server.
     */
    public static String configuredId() {
        return switch (McaQuestsConfig.COMMON.currencyProvider.get()) {
            case VANILLA -> EMERALD_ID;
            case NUMISMATICS -> McaQuestsConfig.COMMON.numismaticsCurrencyItem.get();
            case CUSTOM -> McaQuestsConfig.COMMON.customCurrencyItem.get();
        };
    }

    /**
     * Registry lookup by id. {@code BuiltInRegistries.ITEM} returns {@code AIR} for an unknown id rather
     * than throwing, so an absent mod is detected by that sentinel — not by catching a classload error.
     */
    private static Optional<Item> registryLookup(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(parsed);
        return item == Items.AIR ? Optional.empty() : Optional.of(item);
    }

    private static void warnOnce(String id, CurrencyProviderMode mode) {
        if (!WARNED.add(id)) {
            return;
        }
        String consequence = McaQuestsConfig.COMMON.currencyFallback.get() == CurrencyFallback.EMERALDS
                ? "Falling back to emeralds"
                : "Currency rewards are disabled";
        McaQuests.LOGGER.warn("[MCA: Quests] currencyProvider is {} but item '{}' is not in the item registry"
                + " (is the mod installed?). {} until this is fixed.", mode, id, consequence);
    }

    /** Test hook: clears the once-per-id warning memo. */
    public static void resetWarnings() {
        WARNED.clear();
    }
}
