package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.compat.ClassConstantPool;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
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
 *       {@code Class.forName(name, false, loader)}. Running a Kotlin class's static initialiser to
 *       find out whether it exists would drag in kotlinx-serialization and Kambrik at probe time, on
 *       a thread that has no business doing it.</li>
 *   <li><b>The way into a bounty is a constructor, matched by shape.</b> Bountiful 8.0 keeps a
 *       bounty in a stack's data components and wraps a stack in {@code BountyStack} to read them;
 *       that wrapper's constructor takes one {@link ItemStack} and nothing else. Matching the shape
 *       rather than a parameter list we would have to name keeps the whole read inside vanilla
 *       types.</li>
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

    /** Bountiful's component package, stored dotted; see the class javadoc for why that matters. */
    private static final String PACKAGE = "io.ejekta.bountiful.components.";

    /** The class carrying {@code tryCashIn}; also the class whose bytes the hook probe reads. */
    public static final String BOUNTY_STACK_CLASS = PACKAGE + "BountyStack";

    private static final String O_BOUNTY_STACK = "BountyStack";
    private static final String O_BOUNTY_INFO = "BountyInfo";

    /** The method the cash-in hook targets, and the exact descriptor it must have to be hookable. */
    private static final String CASH_IN_METHOD = "tryCashIn";
    private static final String CASH_IN_DESCRIPTOR = "(Lnet/minecraft/world/entity/player/Player;)Z";

    private enum Kind {
        /** Bound by name, arity and staticness, as every Townstead member is. */
        NAMED,
        /** A constructor, bound by parameter shape: exactly one {@link ItemStack} and nothing else. */
        CONSTRUCTOR
    }

    /** One thing MCA: Quests reads from Bountiful, named relative to {@link #PACKAGE}. */
    public static final class Member {

        private final Kind kind;
        private final String ownerRelative;
        private final String name;
        private final int arity;
        private final boolean optional;
        private final BountifulBridge.Capability capability;

        private Member(Kind kind, String ownerRelative, String name, int arity, boolean optional,
                       BountifulBridge.Capability capability) {
            this.kind = kind;
            this.ownerRelative = ownerRelative;
            this.name = name;
            this.arity = arity;
            this.optional = optional;
            this.capability = capability;
        }

        public BountifulBridge.Capability capability() {
            return capability;
        }

        @Override
        public String toString() {
            return kind == Kind.CONSTRUCTOR
                    ? ownerRelative + "#<init>(ItemStack)"
                    : ownerRelative + "#" + name + "/" + arity;
        }

        /**
         * The erased handle shape: every parameter is {@link Object} and the return is {@link Object}.
         * A method's receiver counts as a parameter and a constructor's does not, which is the whole
         * of the difference between the two kinds here. Nothing Bountiful owns is ever named, on
         * either side of the call.
         */
        private MethodType erasedType() {
            int parameters = kind == Kind.CONSTRUCTOR ? arity : arity + 1;
            return MethodType.methodType(Object.class, Collections.nCopies(parameters, Object.class));
        }
    }

    private static Member virtual(String ownerRelative, String name, int arity,
                                  BountifulBridge.Capability capability) {
        return new Member(Kind.NAMED, ownerRelative, name, arity, false, capability);
    }

    /** A member whose absence is survivable: it binds or it does not, and its capability stands. */
    private static Member optional(String ownerRelative, String name, int arity,
                                   BountifulBridge.Capability capability) {
        return new Member(Kind.NAMED, ownerRelative, name, arity, true, capability);
    }

    /** The one-{@link ItemStack} constructor of {@code ownerRelative}, matched by shape. */
    private static Member constructor(String ownerRelative, BountifulBridge.Capability capability) {
        return new Member(Kind.CONSTRUCTOR, ownerRelative, "<init>", 1, false, capability);
    }

    // ---------------------------------------------------------------------------------------------
    // The manifest — every Bountiful member MCA: Quests reads.
    //
    // Verified against bountiful-neoforge-8.0.0-beta.2.jar. There is no BountyData in Bountiful 8.0:
    // a bounty lives in the stack's data components, and io.ejekta.bountiful.components.BountyStack is
    // the wrapper that reads them. Its constructor only null-checks, so wrapping a stack that carries
    // no bounty is safe -- which is why the rarity read below can start from an ItemStack at all. The
    // rank itself sits on BountyInfo, a Java record whose accessor is rarity() rather than getRarity().
    // See BountifulJarProbeTest.
    // ---------------------------------------------------------------------------------------------

    public static final Member STACK_CTOR =
            constructor(O_BOUNTY_STACK, BountifulBridge.Capability.READ_RARITY);
    public static final Member STACK_INFO =
            virtual(O_BOUNTY_STACK, "getInfo", 0, BountifulBridge.Capability.READ_RARITY);
    public static final Member INFO_RARITY =
            virtual(O_BOUNTY_INFO, "rarity", 0, BountifulBridge.Capability.READ_RARITY);
    public static final Member STACK_OBJS =
            virtual(O_BOUNTY_STACK, "getObjs", 0, BountifulBridge.Capability.READ_OBJECTIVES);
    public static final Member STACK_REWS =
            virtual(O_BOUNTY_STACK, "getRews", 0, BountifulBridge.Capability.READ_OBJECTIVES);

    /**
     * The stack a bounty object came from, used only to key one cash-in against its repeat. Optional
     * because losing it costs a dedupe key and nothing else: the objective and reward counts are what
     * {@code READ_OBJECTIVES} is actually about, and taking the capability down over a key would turn
     * a smaller problem into a larger one.
     */
    public static final Member STACK_STACK =
            optional(O_BOUNTY_STACK, "getStack", 0, BountifulBridge.Capability.READ_OBJECTIVES);

    /** Every member, in declaration order. The single source of truth for what this mod reads. */
    public static final List<Member> MANIFEST =
            List.of(STACK_CTOR, STACK_INFO, INFO_RARITY, STACK_OBJS, STACK_REWS, STACK_STACK);

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
        private final List<String> unresolved;

        private Resolution(Set<BountifulBridge.Capability> capabilities,
                           Map<Member, MethodHandle> resolved, List<String> unresolved) {
            this.capabilities = capabilities;
            this.resolved = resolved;
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
         * <p>Three reflective hops, all erased: the stack is wrapped, the wrapper yields its info
         * record and that record's rarity is an enum constant. Reading the constant's
         * {@link Enum#name()} rather than the object itself is what keeps {@link BountyRarity} ours —
         * a rank Bountiful adds reads as {@code UNKNOWN} here instead of throwing.
         */
        public BountyRarity rarityOf(ItemStack stack) {
            if (!has(BountifulBridge.Capability.READ_RARITY)) {
                return BountyRarity.UNKNOWN;
            }
            try {
                // Bound to a local first: MethodHandle.invoke is signature-polymorphic, so handing it
                // straight to an overloaded method would leave the compiler to guess its return type.
                Object bounty = (Object) resolved.get(STACK_CTOR).invoke(stack);
                return rarityOf(bounty);
            } catch (Throwable t) {
                // A read that fails is a bounty we cannot answer for, which is exactly UNKNOWN. It
                // must never propagate: this runs from a quest check and, later, from inside another
                // mod's own method.
                return BountyRarity.UNKNOWN;
            }
        }

        /**
         * The rarity of a bounty object, for the cash-in hook — which is handed one directly and has
         * no stack of its own to start from.
         */
        public BountyRarity rarityOf(@Nullable Object bountyStack) {
            if (bountyStack == null || !has(BountifulBridge.Capability.READ_RARITY)) {
                return BountyRarity.UNKNOWN;
            }
            try {
                Object info = resolved.get(STACK_INFO).invoke(bountyStack);
                if (info == null) {
                    return BountyRarity.UNKNOWN;
                }
                Object rarity = resolved.get(INFO_RARITY).invoke(info);
                if (rarity instanceof Enum<?> constant) {
                    return BountyRarity.fromName(constant.name());
                }
                // Not an enum constant at all: whatever it is, it is not a rank we can name.
                return BountyRarity.UNKNOWN;
            } catch (Throwable t) {
                return BountyRarity.UNKNOWN;
            }
        }

        /**
         * How many objectives and rewards a bounty lists, as {@code {objectives, rewards}}.
         *
         * <p>Takes the object rather than a stack because the only thing that ever holds one is the
         * cash-in hook, which is handed it by Bountiful itself. Zeroes when {@code READ_OBJECTIVES}
         * did not bind, which is the same shape of answer and needs no guard at the call site.
         */
        public int[] countsOf(@Nullable Object bountyStack) {
            if (bountyStack == null || !has(BountifulBridge.Capability.READ_OBJECTIVES)) {
                return new int[]{0, 0};
            }
            return new int[]{size(STACK_OBJS, bountyStack), size(STACK_REWS, bountyStack)};
        }

        /**
         * The stack a bounty object was read from, or null when {@link #STACK_STACK} did not bind.
         *
         * <p>Only ever used as a dedupe key, so null is an ordinary answer rather than a failure — the
         * hook falls back to a coarser key rather than losing the completion.
         */
        @Nullable
        public ItemStack stackOf(@Nullable Object bountyStack) {
            MethodHandle handle = resolved.get(STACK_STACK);
            if (bountyStack == null || handle == null) {
                return null;
            }
            try {
                return handle.invoke(bountyStack) instanceof ItemStack stack ? stack : null;
            } catch (Throwable t) {
                return null;
            }
        }

        private int size(Member member, Object bountyStack) {
            try {
                Object list = resolved.get(member).invoke(bountyStack);
                return list instanceof Collection<?> collection ? collection.size() : 0;
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    /** A resolution in which nothing bound: Bountiful absent, or the integration switched off. */
    public static Resolution absent() {
        return new Resolution(Set.of(), Map.of(), List.of());
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
        if (loadOrNull(loader, BOUNTY_STACK_CLASS) == null) {
            return absent();
        }

        Map<Member, MethodHandle> resolved = new IdentityHashMap<>();
        List<String> unresolved = new ArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Map<String, Method[]> methodCache = new HashMap<>();

        for (Member member : MANIFEST) {
            MethodHandle handle = null;
            try {
                handle = member.kind == Kind.CONSTRUCTOR
                        ? bindConstructor(lookup, loader, member)
                        : bindByName(lookup, methodsOf(loader, methodCache, member.ownerRelative), member);
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
            if (!member.optional && !resolved.containsKey(member)) {
                bound.remove(member.capability);
            }
        }
        return new Resolution(Collections.unmodifiableSet(bound), resolved, unresolved);
    }

    /**
     * Whether the class file at {@code bountyStackClass} declares the exact method the cash-in hook
     * needs, read as bytes and never loaded.
     *
     * <p>The descriptor is checked as well as the name, because a {@code tryCashIn} taking different
     * arguments is not the method we can observe, and a hook that applied to it would report
     * completions that never happened. Absent or unreadable is a "no": this only decides whether the
     * hooked bridge is offered at all, and a wrong "yes" costs far more than a wrong "no".
     */
    public static boolean tryCashInPresent(@Nullable Path bountyStackClass) {
        if (bountyStackClass == null) {
            return false;
        }
        return ClassConstantPool.declares(ClassConstantPool.utf8Constants(bountyStackClass),
                CASH_IN_METHOD, CASH_IN_DESCRIPTOR);
    }

    /**
     * Where {@code BountyStack.class} sits inside a mod file, built from the dotted class name.
     *
     * <p>Assembled at runtime rather than written out, because the slash form is exactly the needle
     * {@code NoBountifulStaticLinkTest} scans for: a literal {@code "io/ejekta/bountiful/…"} anywhere
     * in our bytecode would trip the tripwire that exists to prove we never link against this mod.
     */
    public static String bountyStackResource() {
        return BOUNTY_STACK_CLASS.replace('.', '/') + ".class";
    }

    // --- binding ---------------------------------------------------------------------------------

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
     * Finds the constructor taking a single {@link ItemStack}.
     *
     * <p>{@link ItemStack} is vanilla, so naming it costs nothing — it is Bountiful's own types that
     * must stay unnamed, and the handle is erased to {@code (Object)Object} so none of them appears in
     * a signature of ours either. On Bountiful 8.0 exactly one constructor matches this shape.
     */
    @Nullable
    private static MethodHandle bindConstructor(MethodHandles.Lookup lookup, ClassLoader loader,
                                                Member member) {
        Class<?> owner = loadOrNull(loader, PACKAGE + member.ownerRelative);
        if (owner == null) {
            return null;
        }
        Constructor<?>[] candidates;
        try {
            candidates = owner.getConstructors();
        } catch (Throwable t) {
            return null;
        }
        for (Constructor<?> candidate : candidates) {
            if (candidate.getParameterCount() != 1
                    || candidate.getParameterTypes()[0] != ItemStack.class) {
                continue;
            }
            try {
                candidate.setAccessible(true);
                return lookup.unreflectConstructor(candidate).asType(member.erasedType());
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
