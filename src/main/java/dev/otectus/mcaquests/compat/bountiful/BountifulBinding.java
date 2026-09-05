package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.compat.ClassConstantPool;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Bountiful at <em>runtime</em>, by name, and reports what bound as
 * {@link BountifulBridge.Capability capabilities} rather than as one boolean.
 *
 * <p>{@code TownsteadBinding}'s design applied to a third optional mod, with two differences that
 * come from Bountiful being written in Kotlin:
 *
 * <ul>
 *   <li><b>Nothing is ever loaded eagerly.</b> Every class is fetched with
 *       {@code Class.forName(name, false, loader)}. Running a Kotlin object's static initialiser to
 *       find out whether it exists would drag in kotlinx-serialization and Kambrik at probe time, on
 *       a thread that has no business doing it.</li>
 *   <li><b>The rarity reader is matched by shape, not by name.</b> Bountiful stores a bounty's rarity
 *       on a separate item-data record, {@code BountyInfo}, read from a stack through its Kotlin
 *       {@code Companion}. That companion inherits its reader from Kambrik, so the method's
 *       <em>name</em> is an implementation detail of a library we do not depend on — but its shape,
 *       one {@link ItemStack} in and one object out, is the contract. Matching the shape survives a
 *       rename; matching the name would not.</li>
 * </ul>
 *
 * <p>Every class name here is a <b>dotted</b> string literal, never the slash form the JVM uses for a
 * real reference — that is what lets {@code NoBountifulStaticLinkTest} treat any slash-form hit as a
 * regression with no exemption for this file.
 *
 * <p><b>Resolution never throws and never returns null.</b> A member that does not bind simply takes
 * its capability down with it, so nothing downstream needs a guard of its own.
 */
public final class BountifulBinding {

    /** Bountiful's bounty package, stored dotted; see the class javadoc for why that matters. */
    private static final String PACKAGE = "io.ejekta.bountiful.bounty.";

    /** The class carrying {@code tryCashIn}; also the class whose bytes the hook probe reads. */
    public static final String BOUNTY_DATA_CLASS = PACKAGE + "BountyData";

    private static final String O_BOUNTY_DATA = "BountyData";
    private static final String O_BOUNTY_INFO = "BountyInfo";
    private static final String O_BOUNTY_INFO_COMPANION = "BountyInfo$Companion";

    /** The static field on {@code BountyInfo} holding the Kotlin companion singleton. */
    private static final String COMPANION_FIELD = "Companion";

    /** The method the cash-in hook targets, and the exact descriptor it must have to be hookable. */
    private static final String CASH_IN_METHOD = "tryCashIn";
    private static final String CASH_IN_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z";

    private enum Kind {
        /** Bound by name, arity and staticness, as every Townstead member is. */
        NAMED,
        /** Bound by parameter and return shape, because the name belongs to somebody else's library. */
        SHAPED
    }

    /** One thing MCA: Quests reads from Bountiful, named relative to {@link #PACKAGE}. */
    public static final class Member {

        private final Kind kind;
        private final String ownerRelative;
        private final String name;
        private final int arity;
        private final BountifulBridge.Capability capability;

        private Member(Kind kind, String ownerRelative, String name, int arity,
                       BountifulBridge.Capability capability) {
            this.kind = kind;
            this.ownerRelative = ownerRelative;
            this.name = name;
            this.arity = arity;
            this.capability = capability;
        }

        public BountifulBridge.Capability capability() {
            return capability;
        }

        @Override
        public String toString() {
            return kind == Kind.SHAPED
                    ? ownerRelative + "#(ItemStack)->" + name
                    : ownerRelative + "#" + name + "/" + arity;
        }

        /**
         * The erased handle shape: every parameter is {@link Object}, including the receiver, and the
         * return is {@link Object}. Nothing Bountiful owns is ever named, on either side of the call.
         */
        private MethodType erasedType() {
            return MethodType.methodType(Object.class, Collections.nCopies(arity + 1, Object.class));
        }
    }

    private static Member virtual(String ownerRelative, String name, int arity,
                                  BountifulBridge.Capability capability) {
        return new Member(Kind.NAMED, ownerRelative, name, arity, capability);
    }

    /**
     * A member found by shape: exactly one {@link ItemStack} parameter and an object return.
     * {@code returns} is documentation only — it says what the returned object is, not what the
     * method is called.
     */
    private static Member shaped(String ownerRelative, String returns,
                                 BountifulBridge.Capability capability) {
        return new Member(Kind.SHAPED, ownerRelative, returns, 1, capability);
    }

    // ---------------------------------------------------------------------------------------------
    // The manifest — every Bountiful member MCA: Quests reads.
    //
    // Verified against Bountiful-6.0.4+1.20.1-forge.jar. BountyInfo$Companion declares no ItemStack
    // method of its own: it inherits the reader from io.ejekta.kambrik.serial.ItemData, where it is
    // generic and so erases to an Object return. That is why the shape match asks for "one ItemStack
    // parameter, reference return" rather than for a BountyInfo return, and why the returned object's
    // own getRarity() is what proves it really was one. See BountifulJarProbeTest.
    // ---------------------------------------------------------------------------------------------

    public static final Member DATA_OBJECTIVES =
            virtual(O_BOUNTY_DATA, "getObjectives", 0, BountifulBridge.Capability.READ_OBJECTIVES);
    public static final Member DATA_REWARDS =
            virtual(O_BOUNTY_DATA, "getRewards", 0, BountifulBridge.Capability.READ_OBJECTIVES);
    public static final Member INFO_RARITY =
            virtual(O_BOUNTY_INFO, "getRarity", 0, BountifulBridge.Capability.READ_RARITY);
    public static final Member INFO_OF_STACK =
            shaped(O_BOUNTY_INFO_COMPANION, O_BOUNTY_INFO, BountifulBridge.Capability.READ_RARITY);

    /** Every member, in declaration order. The single source of truth for what this mod reads. */
    public static final List<Member> MANIFEST =
            List.of(DATA_OBJECTIVES, DATA_REWARDS, INFO_RARITY, INFO_OF_STACK);

    /** The capabilities this manifest can decide. Nothing else is measured against it. */
    public static final Set<BountifulBridge.Capability> DECLARED_CAPABILITIES = declaredCapabilities();

    private static Set<BountifulBridge.Capability> declaredCapabilities() {
        EnumSet<BountifulBridge.Capability> declared = EnumSet.noneOf(BountifulBridge.Capability.class);
        for (Member member : MANIFEST) {
            declared.add(member.capability);
        }
        return Collections.unmodifiableSet(declared);
    }

    // ---------------------------------------------------------------------------------------------
    // Resolution
    // ---------------------------------------------------------------------------------------------

    /** The outcome of resolving {@link #MANIFEST} against one class loader. Immutable. */
    public static final class Resolution {

        private final Set<BountifulBridge.Capability> capabilities;
        private final Map<Member, MethodHandle> resolved;
        @Nullable
        private final Object infoCompanion;
        private final List<String> unresolved;

        private Resolution(Set<BountifulBridge.Capability> capabilities,
                           Map<Member, MethodHandle> resolved, @Nullable Object infoCompanion,
                           List<String> unresolved) {
            this.capabilities = capabilities;
            this.resolved = resolved;
            this.infoCompanion = infoCompanion;
            this.unresolved = List.copyOf(unresolved);
        }

        /** The capabilities whose every declared member bound. */
        public Set<BountifulBridge.Capability> capabilities() {
            return capabilities;
        }

        public boolean has(BountifulBridge.Capability capability) {
            return capabilities.contains(capability);
        }

        /** Members that did not bind, for the status command. */
        public List<String> unresolved() {
            return unresolved;
        }

        /**
         * The rarity of the bounty on {@code stack}, or {@link BountyRarity#UNKNOWN}.
         *
         * <p>Two reflective hops, both erased: the companion turns the stack into a {@code BountyInfo}
         * and that object's {@code getRarity()} yields a Kotlin enum, whose {@code toString} is its
         * constant name. Reading the name rather than the enum is what keeps {@link BountyRarity} ours
         * — a rank Bountiful adds reads as {@code UNKNOWN} here instead of throwing.
         */
        public BountyRarity rarityOf(ItemStack stack) {
            if (!has(BountifulBridge.Capability.READ_RARITY) || infoCompanion == null) {
                return BountyRarity.UNKNOWN;
            }
            try {
                Object info = resolved.get(INFO_OF_STACK).invoke(infoCompanion, stack);
                if (info == null) {
                    return BountyRarity.UNKNOWN;
                }
                Object rarity = resolved.get(INFO_RARITY).invoke(info);
                return rarity == null ? BountyRarity.UNKNOWN : BountyRarity.fromName(rarity.toString());
            } catch (Throwable t) {
                // A read that fails is a bounty we cannot answer for, which is exactly UNKNOWN. It
                // must never propagate: this runs from a quest check and, later, from inside another
                // mod's own method.
                return BountyRarity.UNKNOWN;
            }
        }

        /**
         * How many objectives and rewards a {@code BountyData} lists, as {@code {objectives, rewards}}.
         *
         * <p>Takes the object rather than a stack because the only thing that ever holds one is the
         * cash-in hook, which is handed it by Bountiful itself. Zeroes when {@code READ_OBJECTIVES}
         * did not bind, which is the same shape of answer and needs no guard at the call site.
         */
        public int[] countsOf(@Nullable Object bountyData) {
            if (bountyData == null || !has(BountifulBridge.Capability.READ_OBJECTIVES)) {
                return new int[]{0, 0};
            }
            return new int[]{size(DATA_OBJECTIVES, bountyData), size(DATA_REWARDS, bountyData)};
        }

        private int size(Member member, Object bountyData) {
            try {
                Object list = resolved.get(member).invoke(bountyData);
                return list instanceof Collection<?> collection ? collection.size() : 0;
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    /** A resolution in which nothing bound: Bountiful absent, or the integration switched off. */
    public static Resolution absent() {
        return new Resolution(Set.of(), Map.of(), null, List.of());
    }

    /**
     * Resolves the whole manifest against {@code loader}. Never throws: every failure is recorded and
     * the capability it belonged to simply does not bind.
     *
     * <p>Catching per member is load-bearing rather than tidy. Enumerating a Kotlin class's methods
     * resolves their parameter descriptors, which for Bountiful names kotlinx-serialization and
     * Kambrik types; a version mismatch there throws {@code NoClassDefFoundError} out of
     * {@code getMethods()} itself, and caught here that becomes "the rarity reader is unavailable"
     * instead of a crashed reload.
     */
    public static Resolution resolveAgainst(ClassLoader loader) {
        if (loadOrNull(loader, BOUNTY_DATA_CLASS) == null) {
            return absent();
        }

        Map<Member, MethodHandle> resolved = new IdentityHashMap<>();
        List<String> unresolved = new ArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Map<String, Method[]> methodCache = new HashMap<>();

        for (Member member : MANIFEST) {
            MethodHandle handle = null;
            try {
                Method[] candidates = methodsOf(loader, methodCache, member.ownerRelative);
                handle = member.kind == Kind.SHAPED
                        ? bindByShape(lookup, candidates, member)
                        : bindByName(lookup, candidates, member);
            } catch (Throwable ignored) {
                // Recorded below as an ordinary miss; see the javadoc for why this must not escape.
            }
            if (handle == null) {
                unresolved.add(member.toString());
            } else {
                resolved.put(member, handle);
            }
        }

        EnumSet<BountifulBridge.Capability> bound = EnumSet.copyOf(DECLARED_CAPABILITIES);
        for (Member member : MANIFEST) {
            if (!resolved.containsKey(member)) {
                bound.remove(member.capability);
            }
        }

        Object companion = bound.contains(BountifulBridge.Capability.READ_RARITY)
                ? companionOf(loader)
                : null;
        if (companion == null) {
            // Without the singleton the reader cannot be called, so the capability is not there
            // however well its method bound.
            bound.remove(BountifulBridge.Capability.READ_RARITY);
        }
        return new Resolution(Collections.unmodifiableSet(bound), resolved, companion, unresolved);
    }

    /**
     * Whether the class file at {@code bountyDataClass} declares the exact method the cash-in hook
     * needs, read as bytes and never loaded.
     *
     * <p>The descriptor is checked as well as the name, because a {@code tryCashIn} taking different
     * arguments is not the method we can observe, and a hook that applied to it would report
     * completions that never happened. Absent or unreadable is a "no": this only decides whether the
     * hooked bridge is offered at all, and a wrong "yes" costs far more than a wrong "no".
     */
    public static boolean tryCashInPresent(@Nullable Path bountyDataClass) {
        if (bountyDataClass == null) {
            return false;
        }
        return ClassConstantPool.declares(ClassConstantPool.utf8Constants(bountyDataClass),
                CASH_IN_METHOD, CASH_IN_DESCRIPTOR);
    }

    /**
     * Where {@code BountyData.class} sits inside a mod file, built from the dotted class name.
     *
     * <p>Assembled at runtime rather than written out, because the slash form is exactly the needle
     * {@code NoBountifulStaticLinkTest} scans for: a literal {@code "io/ejekta/bountiful/…"} anywhere
     * in our bytecode would trip the tripwire that exists to prove we never link against this mod.
     */
    public static String bountyDataResource() {
        return BOUNTY_DATA_CLASS.replace('.', '/') + ".class";
    }

    // --- binding ---------------------------------------------------------------------------------

    /** The Kotlin companion singleton off {@code BountyInfo.Companion}, or null. */
    @Nullable
    private static Object companionOf(ClassLoader loader) {
        Class<?> owner = loadOrNull(loader, PACKAGE + O_BOUNTY_INFO);
        if (owner == null) {
            return null;
        }
        try {
            Field field = owner.getField(COMPANION_FIELD);
            // Reading a static field does initialise its declaring class -- unavoidable, and safe
            // here in a way the presence probe is not: by this point BountyData has already resolved,
            // so Bountiful is installed and loaded, and BountyInfo is a plain data holder.
            return Modifier.isStatic(field.getModifiers()) ? field.get(null) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Finds a method by name, arity and staticness — never by parameter type. */
    @Nullable
    private static MethodHandle bindByName(MethodHandles.Lookup lookup, Method[] candidates,
                                           Member member) {
        for (Method candidate : candidates) {
            if (!candidate.getName().equals(member.name)
                    || candidate.getParameterCount() != member.arity
                    || Modifier.isStatic(candidate.getModifiers())) {
                continue;
            }
            try {
                candidate.setAccessible(true);
                return lookup.unreflect(candidate).asType(member.erasedType());
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /**
     * Finds the one public instance method taking a single {@link ItemStack} and returning an object.
     *
     * <p>{@link ItemStack} is vanilla, so naming it costs nothing — it is Bountiful's own types that
     * must stay unnamed. The return is deliberately not constrained to a particular class because
     * Kambrik's reader is generic and erases to {@link Object}; on Bountiful 6.0.4 exactly one method
     * matches this shape.
     */
    @Nullable
    private static MethodHandle bindByShape(MethodHandles.Lookup lookup, Method[] candidates,
                                            Member member) {
        for (Method candidate : candidates) {
            if (Modifier.isStatic(candidate.getModifiers())
                    || candidate.getParameterCount() != 1
                    || candidate.getParameterTypes()[0] != ItemStack.class
                    || candidate.getReturnType().isPrimitive()) {
                continue;
            }
            try {
                candidate.setAccessible(true);
                return lookup.unreflect(candidate).asType(member.erasedType());
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /** Every public method of an owner, resolved once; a whole-class failure reads as no methods. */
    private static Method[] methodsOf(ClassLoader loader, Map<String, Method[]> cache,
                                      String ownerRelative) {
        return cache.computeIfAbsent(ownerRelative, relative -> {
            Class<?> owner = loadOrNull(loader, PACKAGE + relative);
            if (owner == null) {
                return new Method[0];
            }
            try {
                return owner.getMethods();
            } catch (Throwable t) {
                return new Method[0];
            }
        });
    }

    /**
     * {@code initialize = false} is not optional here. Bountiful is Kotlin: running a class's static
     * initialiser at probe time would build serializers and reach into Kambrik, on whatever thread
     * happened to ask whether the mod is installed.
     */
    @Nullable
    private static Class<?> loadOrNull(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable t) {
            return null;
        }
    }

    private BountifulBinding() {
    }
}
