package dev.otectus.mcaquests.compat.mca;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Decides which of the MCA Gift hook's four variants is applied, and reports what happened.
 *
 * <p>MCA has repackaged twice, and the two axes vary independently, so this mod ships one mixin per
 * candidate package root and exactly one of them can ever have a class to attach to. <b>Three variants
 * being skipped is the healthy case</b>, which is the whole reason the outcome is recorded per target
 * in {@link McaGiftHookProbe} instead of in a single slot.
 *
 * <p>A config plugin rather than {@code "required": false} alone, for the same reason the Bountiful
 * hook has one: "the class is not here" is only one of the ways this hook can be wrong. The other is
 * an MCA build whose {@code handle} is not the method we can route — a different signature, a rename,
 * a refactor — and a mixin that silently failed to find its target would leave Gift looking supported
 * while it quietly hands quest items over as ordinary presents. So the target's bytes are checked
 * first, and the result is verified again after transformation.
 *
 * <p><b>It lives outside the {@code mixin} package on purpose.</b> A config plugin is constructed
 * before its own configuration's package is processed, so a plugin sitting inside that package is a
 * class-loading cycle waiting to happen.
 *
 * <p><b>Nothing MCA owns is ever loaded here.</b> Targets are dotted strings, their bytes are read
 * through Mixin's own provider, and every decision is made from a {@link ClassNode} — which is also
 * what keeps this class free of the internal-form MCA names {@code NoMcaStaticLinkTest} scans for. The
 * mod-presence check goes through {@link LoadingModList} because the ordinary {@code ModList} does not
 * exist yet at the point mixins apply.
 */
public final class McaGiftMixinPlugin implements IMixinConfigPlugin {

    /** MCA's mod id, as the loader knows it. */
    private static final String MCA_MOD_ID = "mca";

    /** The method the hook targets, and the exact shape it must have to be the one we mean. */
    private static final String HANDLE_METHOD = "handle";
    private static final String HANDLE_DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Z";

    @Override
    public void onLoad(String mixinPackage) {
        // Nothing to prepare. Every decision is made per target, in shouldApplyMixin, from bytes read
        // there -- and MCA's presence is recorded at the same time, because that is the first moment
        // LoadingModList can be asked.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /**
     * True only for the one root MCA actually ships, and only when its class really declares the
     * method this hook routes.
     *
     * <p>The three questions are kept apart in the record. MCA absent, or this root not being the live
     * one, is ordinary and reports {@link McaGiftHookProbe.State#SKIPPED}. A root that is present but
     * whose {@code handle} does not match reports the same state with a reason a bug report can carry.
     * Only failing to <em>ask</em> is a {@link McaGiftHookProbe.State#FAILED}.
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean present = mcaPresent();
        McaGiftHookProbe.mcaPresent(present);
        if (!present) {
            McaGiftHookProbe.skipped(targetClassName, "MCA is not installed");
            return false;
        }
        ClassNode target;
        try {
            target = classNodeOf(targetClassName);
        } catch (Throwable t) {
            // Could not read the class at all. For three of the four variants this is the expected
            // answer -- that root does not exist in this MCA -- so it is a skip, not a failure.
            McaGiftHookProbe.skipped(targetClassName, "not present in this MCA build");
            return false;
        }
        if (target == null) {
            McaGiftHookProbe.skipped(targetClassName, "not present in this MCA build");
            return false;
        }
        if (findHandle(target) == null) {
            McaGiftHookProbe.failed(targetClassName,
                    HANDLE_METHOD + " is absent or has a different signature");
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Not consulted. Each mixin declares its single target itself.
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
        // Nothing to do beforehand: verifying the result is worth more than inspecting the input.
    }

    /**
     * Verifies that the injected call really is in the transformed method, and records the outcome.
     *
     * <p>Trusting Mixin to have applied the injection would defeat the point. The injector carries
     * {@code require = 0} — it has to, because three of the four variants have no target at all — so an
     * {@code @At} that matched nothing is not an error, and would leave a hook that exists, applies
     * cleanly, and routes nothing.
     *
     * <p><b>What the evidence actually looks like matters here.</b> Mixin does not paste the handler's
     * body into the target: it merges the handler in as a method of the target class and injects a call
     * to <em>that</em> — an {@code INVOKESPECIAL} on the target's own {@code mcaquests$} method, not a
     * static call to this mod. Looking for the latter alone would report a healthy hook as broken, so
     * both shapes are accepted: the merged callback, and a directly inlined static call.
     */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
        MethodNode handle = findHandle(targetClass);
        if (handle != null && invokesHandler(targetClass, handle)) {
            McaGiftHookProbe.applied(targetClassName);
        } else {
            McaGiftHookProbe.failed(targetClassName, "injected call not present after apply");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The decisions themselves, kept static and Mixin-free so they can be asserted against a
    // synthetic ClassNode rather than against a transformed copy of somebody else's mod.
    // ---------------------------------------------------------------------------------------------

    /** The {@code handle} with exactly the descriptor the hook needs, or null. */
    @Nullable
    static MethodNode findHandle(ClassNode target) {
        if (target.methods == null) {
            return null;
        }
        for (MethodNode method : target.methods) {
            if (HANDLE_METHOD.equals(method.name) && HANDLE_DESCRIPTOR.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    /**
     * True when {@code method} carries our injection.
     *
     * <p>Two accepted shapes, because Mixin chooses between them: the ordinary one is a call to the
     * callback it merged into the target class, recognisable by this mod's own method-name prefix; the
     * other is a direct static call to the handler, which is what an inlined callback leaves behind.
     * Anything else means the {@code @At} matched nothing.
     */
    static boolean invokesHandler(ClassNode target, MethodNode method) {
        if (method.instructions == null) {
            return false;
        }
        String handler = handlerOwner();
        for (AbstractInsnNode insn : method.instructions) {
            if (!(insn instanceof MethodInsnNode call)) {
                continue;
            }
            if (insn.getOpcode() == Opcodes.INVOKESTATIC && handler.equals(call.owner)) {
                return true;
            }
            if (call.name != null && call.name.startsWith(CALLBACK_PREFIX)
                    && (target.name == null || target.name.equals(call.owner))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The prefix every method this mod merges into somebody else's class carries.
     *
     * <p>Mixin's own convention, and the project's: a name nobody else can collide with is also the
     * only reliable way to recognise our own injected call in a transformed method.
     */
    private static final String CALLBACK_PREFIX = "mcaquests$";

    /**
     * The handler's internal name, built from the class rather than written out — a literal would be a
     * second copy of a name only the compiler should be maintaining.
     */
    static String handlerOwner() {
        return McaGiftHookEvents.class.getName().replace('.', '/');
    }

    /** Whether the loader has a mod file for MCA at all. Any throw reads as "no". */
    private static boolean mcaPresent() {
        try {
            return LoadingModList.get().getModFileById(MCA_MOD_ID) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The target's bytes as a tree, asked of Mixin's own provider so nothing is loaded and no class
     * initialiser runs. Tried in both name forms because the spelling a provider accepts differs
     * between Mixin's services, and a wrong guess here would read as "this root is absent".
     */
    @Nullable
    private static ClassNode classNodeOf(String targetClassName) throws Throwable {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Throwable first) {
            return MixinService.getService().getBytecodeProvider()
                    .getClassNode(targetClassName.replace('.', '/'));
        }
    }
}
