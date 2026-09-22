package dev.otectus.mcaquests.support;

import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadCompat;
import dev.otectus.mcaquests.compat.TownsteadProfessionTrackView;
import dev.otectus.mcaquests.compat.TownsteadStatus;

import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A Townstead bridge for tests: bound with exactly the capabilities given, answering every read with
 * nothing unless a test supplies it. Installed through {@link TownsteadCompat#setBridgeForTest}; call
 * {@link #uninstall()} afterwards.
 */
public final class FakeTownstead {

    private FakeTownstead() {
    }

    public static void install(Set<TownsteadCapability> capabilities) {
        install(capabilities, Map.of());
    }

    public static void install(Set<TownsteadCapability> capabilities, Map<String, Map<String, Integer>> spiritSources) {
        Set<TownsteadCapability> bound = capabilities.isEmpty()
                ? EnumSet.noneOf(TownsteadCapability.class) : EnumSet.copyOf(capabilities);
        TownsteadBridge bridge = (TownsteadBridge) Proxy.newProxyInstance(TownsteadBridge.class.getClassLoader(),
                new Class<?>[]{TownsteadBridge.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "status" -> bound.isEmpty() ? TownsteadStatus.DISABLED
                            : bound.size() == TownsteadCapability.values().length ? TownsteadStatus.FULL
                            : TownsteadStatus.PARTIAL;
                    case "isAvailable" -> !bound.isEmpty();
                    case "has" -> bound.contains((TownsteadCapability) args[0]);
                    case "capabilities" -> Set.copyOf(bound);
                    case "detectedVersion" -> "test";
                    case "spiritContributions" -> spiritSources.getOrDefault((String) args[0], Map.of());
                    case "professionTrack" -> TownsteadProfessionTrackView.none((String) args[0]);
                    case "unresolvedMembers" -> List.of();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakeTownstead" + bound;
                    default -> defaultFor(method.getReturnType());
                });
        TownsteadCompat.setBridgeForTest(bridge);
    }

    public static void uninstall() {
        TownsteadCompat.resetForTest();
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == Optional.class) return Optional.empty();
        if (type == Set.class) return Set.of();
        if (type == List.class) return List.of();
        if (type == Map.class) return Map.of();
        return null;
    }
}
