package dev.otectus.mcaquests.compat.bountiful;

import net.minecraftforge.fml.loading.LoadingModList;
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
 * Decides whether the Bountiful cash-in hook is applied at all, and reports what happened.
 *
 * <p>A config plugin rather than {@code "required": false} alone, because "the mod is absent" is only
 * one of the two ways this hook can be wrong. The other is a Bountiful build whose
 * {@code tryCashIn} is not the method we can observe — a different signature, a rename, a refactor
 * into another class — and a mixin that silently failed to find its target would leave bounty quests
 * sitting at zero with nothing in the log to explain it. So the target's bytes are checked first, and
 * the outcome is written to {@link BountifulHookProbe} either way.
 *
 * <p><b>It lives outside the {@code mixin} package on purpose.</b> A config plugin is constructed
 * before its own configuration's package is processed, so a plugin sitting inside that package is a
 * class-loading cycle waiting to happen.
 *
 * <p><b>Nothing Bountiful owns is ever loaded here.</b> The class is named as a dotted string, its
 * bytes are read through Mixin's own bytecode provider, and every decision is made from a
 * {@link ClassNode}. It is also why the mod-presence check goes through {@link LoadingModList}: the
 * ordinary {@code ModList} does not exist yet at the point mixins apply.
 */
public final class BountifulMixinPlugin implements IMixinConfigPlugin {

    /** The method the hook targets, and the exact shape it must have to be the one we mean. */
    private static final String CASH_IN_METHOD = "tryCashIn";
    private static final String CASH_IN_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z";

    @Override
    public void onLoad(String mixinPackage) {
        // Nothing to prepare. Every decision this plugin makes is made per target, in
        // shouldApplyMixin, from bytes it reads there.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /**
     * True only when Bountiful is installed <em>and</em> the class really declares the method the hook
     * injects into.
     *
     * <p>The two questions are deliberately separate. A missing mod is the ordinary case and reports
     * {@link BountifulHookProbe.State#SKIPPED}; a mod that is installed but whose method does not
     * match reports the same state with a reason a bug report can carry. Only failing to <em>ask</em>
     * the question is a {@link BountifulHookProbe.State#FAILED}.
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!bountifulPresent()) {
            BountifulHookProbe.skipped("Bountiful is not installed");
            return false;
        }
        ClassNode target;
        try {
            target = classNodeOf(targetClassName);
        } catch (Throwable t) {
            // Could not read the class at all: not "the method is absent" but "we do not know", which
            // is the one outcome worth surfacing as a failure.
            BountifulHookProbe.fail("could not read " + targetClassName + ": " + t);
            return false;
        }
        if (target == null || findCashIn(target) == null) {
            BountifulHookProbe.skipped(CASH_IN_METHOD + " is absent or has a different signature");
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Not consulted. The single target is declared by the mixin itself.
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
        // Nothing to do beforehand: the hook only ever adds calls, and verifying the result is worth
        // more than inspecting the input.
    }

    /**
     * Verifies that the handler calls really are in the transformed method, and records the outcome.
     *
     * <p>Trusting Mixin to have applied the injection would defeat the point of the probe: both
     * injectors carry {@code require = 0}, so an {@code @At} that matched nothing is not an error and
     * would leave a hook that exists, applies cleanly, and observes nothing. Finding our own
     * {@code INVOKESTATIC} is the only evidence that survives that.
     */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
        MethodNode cashIn = findCashIn(targetClass);
        if (cashIn != null && invokesHandler(targetClass, cashIn)) {
            BountifulHookProbe.applied();
        } else {
            BountifulHookProbe.fail("handler not present after apply");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The decisions themselves, kept static and Mixin-free so they can be asserted against a
    // synthetic ClassNode rather than against a transformed copy of somebody else's mod.
    // ---------------------------------------------------------------------------------------------

    /** The {@code tryCashIn} with exactly the descriptor the hook needs, or null. */
    @Nullable
    static MethodNode findCashIn(ClassNode target) {
        if (target.methods == null) {
            return null;
        }
        for (MethodNode method : target.methods) {
            if (CASH_IN_METHOD.equals(method.name) && CASH_IN_DESCRIPTOR.equals(method.desc)) {
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
     *
     * <p>The first shape used to be missing here, and it is the shape Mixin 0.8.5 actually emits for a
     * healthy hook — so a working Bountiful integration reported itself FAILED, and the probe that
     * exists to catch a silent no-op was instead manufacturing a false alarm about one.
     */
    static boolean invokesHandler(ClassNode target, MethodNode method) {
        if (method.instructions == null) {
            return false;
        }
        String owner = handlerOwner();
        for (AbstractInsnNode insn : method.instructions) {
            if (!(insn instanceof MethodInsnNode call)) {
                continue;
            }
            if (insn.getOpcode() == Opcodes.INVOKESTATIC && owner.equals(call.owner)) {
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
        return BountifulHookEvents.class.getName().replace('.', '/');
    }

    /** Whether Forge has a mod file for Bountiful at all. Any throw reads as "no". */
    private static boolean bountifulPresent() {
        try {
            return LoadingModList.get().getModFileById(BountifulBridge.MOD_ID) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The target's bytes as a tree, asked of Mixin's own provider so nothing is loaded and no class
     * initialiser runs. Tried in both name forms because the spelling a provider accepts differs
     * between Mixin's services, and a wrong guess here would read as "the method is absent".
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
