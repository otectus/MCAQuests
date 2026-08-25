package dev.otectus.mcaquests.compat;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a dot path against MCA: Quests' Townstead view records (Townstead spec §4.2).
 *
 * <p>Townstead ships its own {@code TownsteadQuery.resolve(Object, String)}, and this deliberately
 * does not call it. Pack semantics must be ours: if Townstead changed how a path resolved, every
 * datapack written against this mod would change meaning underneath its author. Walking our own
 * records instead means the query contract is versioned with MCA: Quests and nothing else.
 *
 * <p>A segment steps through a {@link Map} by key, a {@link List} by numeric index, and any other
 * object by <b>zero-argument accessor</b> — which covers record components and the small derived
 * helpers on the views alike, so {@code needs.energy} and {@code building.level} read exactly like
 * {@code needs.hunger} even though only the last is a stored field.
 *
 * <p>Accessors are resolved once per class and cached as method handles. These run inside the
 * once-per-second objective pass, so per-call reflection would be the wrong shape entirely.
 */
public final class TownsteadPaths {

    /** Never derived from a foreign class — only ever MCA: Quests' own view records. */
    private static final Map<Class<?>, Map<String, MethodHandle>> ACCESSORS = new ConcurrentHashMap<>();

    private static final Map<String, MethodHandle> NONE = Map.of();

    private TownsteadPaths() {
    }

    /**
     * The value at {@code path}, or empty when any segment along the way is missing or null. An empty
     * path returns {@code root} itself, which is what makes {@code "exists"} on a whole source work.
     */
    public static Optional<Object> resolve(@Nullable Object root, List<String> path) {
        Object current = root;
        for (String segment : path) {
            if (current == null) {
                return Optional.empty();
            }
            current = step(current, segment);
        }
        return Optional.ofNullable(current);
    }

    @Nullable
    private static Object step(Object current, String segment) {
        if (current instanceof Map<?, ?> map) {
            return map.get(segment);
        }
        if (current instanceof List<?> list) {
            int index = index(segment);
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        if (current instanceof Optional<?> optional) {
            // Views never hold Optionals today, but a future one might; unwrap rather than dead-end.
            return optional.isPresent() ? step(optional.get(), segment) : null;
        }
        MethodHandle accessor = accessorsFor(current.getClass()).get(segment);
        if (accessor == null) {
            return null;
        }
        try {
            return accessor.invoke(current);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int index(String segment) {
        if (segment.isEmpty() || segment.length() > 9) {
            return -1;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(segment);
    }

    /**
     * Every zero-argument public accessor of a view class, by name. Record components come first so a
     * component always wins over a same-named derived method, which cannot happen today but would be a
     * silent semantic change if it ever did.
     */
    private static Map<String, MethodHandle> accessorsFor(Class<?> type) {
        return ACCESSORS.computeIfAbsent(type, TownsteadPaths::buildAccessors);
    }

    private static Map<String, MethodHandle> buildAccessors(Class<?> type) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Map<String, MethodHandle> accessors = new HashMap<>();

        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0
                    || Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() == void.class
                    || method.getDeclaringClass() == Object.class
                    || isObjectContract(method.getName())) {
                continue;
            }
            try {
                accessors.put(method.getName(), lookup.unreflect(method).asType(
                        java.lang.invoke.MethodType.methodType(Object.class, Object.class)));
            } catch (Throwable ignored) {
                // An inaccessible accessor is simply not addressable by a path.
            }
        }

        RecordComponent[] components = type.getRecordComponents();
        if (components != null) {
            for (RecordComponent component : components) {
                try {
                    accessors.put(component.getName(), lookup.unreflect(component.getAccessor()).asType(
                            java.lang.invoke.MethodType.methodType(Object.class, Object.class)));
                } catch (Throwable ignored) {
                    // Same: unreachable component, unaddressable path.
                }
            }
        }

        return accessors.isEmpty() ? NONE : Collections.unmodifiableMap(accessors);
    }

    private static boolean isObjectContract(String name) {
        return name.equals("hashCode") || name.equals("toString") || name.equals("clone");
    }
}
