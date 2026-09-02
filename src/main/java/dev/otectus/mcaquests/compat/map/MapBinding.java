package dev.otectus.mcaquests.compat.map;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a mapping mod at <em>runtime</em>, by name, and reports what bound.
 *
 * <p>This is {@code McaBinding} and {@code TownsteadBinding}'s design applied to a third and fourth
 * optional mod. Neither JourneyMap nor Xaero's Minimap is redistributable, neither is on a Maven this
 * build can reach, and the jars in {@code libs/} are gitignored — so compiling against them would put
 * the build's ability to produce a working jar in the hands of whoever happens to have downloaded two
 * files. Resolving them by name costs a manifest and gives that up for nothing.
 *
 * <h2>Positional parameter hints</h2>
 *
 * <p>The one thing the older bindings could not do and this one must. Both mods carry overloads that
 * differ only in the middle:
 *
 * <ul>
 *   <li>JourneyMap's {@code WaypointFactory.createWaypoint} has two five-argument forms whose only
 *       difference is a {@code String} dimension id versus a {@code ResourceKey}, and a
 *       four-argument form whose third parameter is the dimension where the five-argument form's is
 *       the <em>name</em> — bind the wrong one and every waypoint is called after the dimension it
 *       is in.</li>
 *   <li>Xaero's {@code Waypoint} constructor has a nine-argument all-primitive form and a
 *       nine-argument form taking two of its own enums. Only the first can be called without naming
 *       a Xaero type, so "any nine-argument constructor" is not good enough.</li>
 * </ul>
 *
 * <p>So a {@link Member} may pin any parameter position to a type. Every hint is a JDK or Minecraft
 * class — hinting a <em>mod</em> type would be the linkage this whole arrangement exists to avoid, and
 * is exactly why the mod-typed overloads are the ones left unbound.
 *
 * <h2>The contract</h2>
 *
 * <p><b>Resolution never throws and never returns null.</b> An unresolved member becomes a constant
 * stub returning its type's default, so call sites need no guards. Every handle is adapted to an
 * erased shape whose parameters are all {@link Object}, so a mod value passes through as a reference
 * this mod never names.
 */
public final class MapBinding {

    private MapBinding() {
    }

    public enum Kind { CLASS, VIRTUAL, STATIC, CONSTRUCTOR, ENUM_CONSTANT }

    /** One thing MCA: Quests needs from a mapping mod, named relative to its package root. */
    public static final class Member {

        private final Kind kind;
        private final String ownerRelative;
        private final String name;
        private final Class<?> returnType;
        private final int arity;
        private final Class<?>[] paramHints;

        private Member(Kind kind, String ownerRelative, String name, Class<?> returnType, int arity,
                       Class<?>[] paramHints) {
            this.kind = kind;
            this.ownerRelative = ownerRelative;
            this.name = name;
            this.returnType = returnType;
            this.arity = arity;
            this.paramHints = paramHints;
        }

        /** A class that merely has to exist — a probe, or the owner of an enum constant. */
        public static Member cls(String ownerRelative) {
            return new Member(Kind.CLASS, ownerRelative, "", void.class, 0, new Class<?>[0]);
        }

        /** An instance method. {@code hints} may be shorter than {@code arity}; nulls mean "any". */
        public static Member virtual(String ownerRelative, String name, Class<?> returnType, int arity,
                                     Class<?>... hints) {
            return new Member(Kind.VIRTUAL, ownerRelative, name, returnType, arity, hints);
        }

        /** A static method. */
        public static Member statik(String ownerRelative, String name, Class<?> returnType, int arity,
                                    Class<?>... hints) {
            return new Member(Kind.STATIC, ownerRelative, name, returnType, arity, hints);
        }

        /** A constructor. */
        public static Member constructor(String ownerRelative, int arity, Class<?>... hints) {
            return new Member(Kind.CONSTRUCTOR, ownerRelative, "<init>", Object.class, arity, hints);
        }

        public String ownerRelative() {
            return ownerRelative;
        }

        public String describe() {
            return ownerRelative + '.' + (kind == Kind.CLASS ? "<class>" : name) + '/' + arity;
        }

        private MethodType erasedType() {
            int params = switch (kind) {
                case VIRTUAL -> arity + 1; // receiver first
                case STATIC, CONSTRUCTOR -> arity;
                case CLASS, ENUM_CONSTANT -> 0;
            };
            return MethodType.methodType(returnType, Collections.nCopies(params, Object.class));
        }

        private boolean parametersMatch(Class<?>[] actual) {
            if (actual.length != arity) {
                return false;
            }
            for (int i = 0; i < paramHints.length && i < actual.length; i++) {
                if (paramHints[i] != null && !paramHints[i].equals(actual[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    /** What one mapping mod's manifest resolved to in this game. */
    public static final class Resolution {

        private final String modName;
        private final boolean present;
        private final Map<Member, Object> resolved;
        private final List<String> missing;

        private Resolution(String modName, boolean present, Map<Member, Object> resolved,
                           List<String> missing) {
            this.modName = modName;
            this.present = present;
            this.resolved = resolved;
            this.missing = List.copyOf(missing);
        }

        /** The mod is installed and every member of its manifest bound. */
        public boolean isBound() {
            return present && missing.isEmpty();
        }

        /** The mod is installed but some member did not bind — a version this build does not know. */
        public boolean isPartial() {
            return present && !missing.isEmpty();
        }

        public String modName() {
            return modName;
        }

        /** Members that did not bind, for {@code /mcaquests debug waypoints}. */
        public List<String> missing() {
            return missing;
        }

        /** The resolved handle, or a stub returning the member's default. Never null. */
        public MethodHandle handle(Member member) {
            Object value = resolved.get(member);
            return value instanceof MethodHandle h ? h : MethodHandles.empty(member.erasedType());
        }

        /** The resolved class, or null. */
        @Nullable
        public Class<?> cls(Member member) {
            Object value = resolved.get(member);
            return value instanceof Class<?> c ? c : null;
        }

        /**
         * The named constant of a resolved enum class, or null.
         *
         * <p>Reading one initialises the enum and forces the JVM to resolve every method descriptor on
         * it, so a mod compiled against something this game does not have throws
         * {@code NoClassDefFoundError} from {@code getEnumConstants} itself. Caught here, that reads
         * as "the singleton did not resolve" and disables one integration, which is the contract every
         * other member of this class already keeps.
         */
        @Nullable
        public Object enumConstant(Member enumClass, String constant) {
            Class<?> type = cls(enumClass);
            if (type == null || !type.isEnum()) {
                return null;
            }
            try {
                for (Object candidate : type.getEnumConstants()) {
                    if (candidate instanceof Enum<?> e && e.name().equals(constant)) {
                        return candidate;
                    }
                }
            } catch (Throwable ignored) {
                return null;
            }
            return null;
        }
    }

    /**
     * Resolves {@code manifest} against {@code loader}.
     *
     * <p>{@code root} is stored and passed <b>dotted</b>, never in internal slash form — that is what
     * lets {@code NoJourneyMapStaticLinkTest} and {@code NoXaeroStaticLinkTest} byte-scan compiled
     * classes for slash-form references and treat any hit as a regression, with no exemption for the
     * files that do the binding.
     */
    public static Resolution resolve(String modName, String root, String probeRelative,
                                     List<Member> manifest, ClassLoader loader) {
        Class<?> probe = loadOrNull(loader, root + probeRelative);
        if (probe == null) {
            return new Resolution(modName, false, Map.of(), List.of());
        }
        Map<Member, Object> resolved = new IdentityHashMap<>();
        List<String> missing = new ArrayList<>();
        for (Member member : manifest) {
            Object value = null;
            try {
                value = bind(root, member, loader);
            } catch (Throwable ignored) {
                // Enumerating a class's methods resolves their descriptors, so a mod built against a
                // dependency this game does not have throws from getMethods() itself. Caught per
                // member, that reads as "this did not bind" rather than taking the game down.
            }
            if (value == null) {
                missing.add(member.describe());
            } else {
                resolved.put(member, value);
            }
        }
        return new Resolution(modName, true, resolved, missing);
    }

    /** A resolution for a mod that is not installed. */
    public static Resolution absent(String modName) {
        return new Resolution(modName, false, Map.of(), List.of());
    }

    @Nullable
    private static Object bind(String root, Member member, ClassLoader loader) throws Throwable {
        Class<?> owner = loadOrNull(loader, root + member.ownerRelative);
        if (owner == null) {
            return null;
        }
        if (member.kind == Kind.CLASS) {
            return owner;
        }
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        if (member.kind == Kind.CONSTRUCTOR) {
            for (Constructor<?> candidate : owner.getConstructors()) {
                if (!member.parametersMatch(candidate.getParameterTypes())) {
                    continue;
                }
                candidate.setAccessible(true);
                return lookup.unreflectConstructor(candidate).asType(member.erasedType());
            }
            return null;
        }
        for (Method candidate : owner.getMethods()) {
            // Bridges are skipped rather than deprioritised: getMethods() has no defined order, so
            // binding a covariant bridge would be a coin flip a passing probe test could not detect.
            if (candidate.isBridge()
                    || !candidate.getName().equals(member.name)
                    || Modifier.isStatic(candidate.getModifiers()) != (member.kind == Kind.STATIC)
                    || !member.parametersMatch(candidate.getParameterTypes())) {
                continue;
            }
            candidate.setAccessible(true);
            return lookup.unreflect(candidate).asType(member.erasedType());
        }
        return null;
    }

    @Nullable
    private static Class<?> loadOrNull(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader); // initialize = false, deliberately
        } catch (Throwable t) {
            return null;
        }
    }
}
