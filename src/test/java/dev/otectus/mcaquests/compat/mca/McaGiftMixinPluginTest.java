package dev.otectus.mcaquests.compat.mca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two decisions {@code McaGiftMixinPlugin} makes, asserted against a synthetic class rather than
 * against a transformed copy of MCA.
 *
 * <p>Both matter more here than for an ordinary hook. The injector carries {@code require = 0},
 * because three of the four package-root variants have no target class at all, so an {@code @At} that
 * matched nothing is not an error — and the second decision is what the quest menu's "or gift it to
 * them" hint is drawn from.
 *
 * <p>The post-apply case is the one that is easy to get wrong, and this test exists because it was:
 * Mixin does not paste a callback's body into the target method. It merges the callback in as a method
 * <em>of the target class</em> and injects a call to that, so the instruction left behind in
 * {@code handle} names MCA's own class and this mod's method-name prefix — not this mod's handler
 * class. A verification that only looked for the latter would report every healthy installation as
 * broken.
 */
class McaGiftMixinPluginTest {

    private static final String DESCRIPTOR =
            "(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Z";

    /** Dotted in the source, internal on the wire; this is the internal form Mixin writes. */
    private static final String TARGET = "forge/net/mca/entity/interaction/VillagerCommandHandler";

    private static ClassNode classWith(MethodNode... methods) {
        ClassNode node = new ClassNode();
        node.name = TARGET;
        node.methods = new ArrayList<>();
        for (MethodNode method : methods) {
            node.methods.add(method);
        }
        return node;
    }

    private static MethodNode method(String name, String descriptor) {
        return new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, name, descriptor, null, null);
    }

    @Test
    @DisplayName("the target method is found only with the exact descriptor")
    void findsOnlyTheRightMethod() {
        assertNotNull(McaGiftMixinPlugin.findHandle(classWith(method("handle", DESCRIPTOR))));
        assertNull(McaGiftMixinPlugin.findHandle(classWith(method("handle", "()Z"))),
                "a handle taking something else is not the command entry point, and routing gifts "
                        + "through it would cancel MCA actions this mod knows nothing about");
        assertNull(McaGiftMixinPlugin.findHandle(classWith(
                method("handle", "(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)V"))),
                "a void handle is a different method with the same name");
        assertNull(McaGiftMixinPlugin.findHandle(classWith(method("giveGift", DESCRIPTOR))));
        assertNull(McaGiftMixinPlugin.findHandle(new ClassNode()), "no methods at all is not a crash");
    }

    @Test
    @DisplayName("the merged callback Mixin actually injects reads as applied")
    void mergedCallbackIsFound() {
        MethodNode handle = method("handle", DESCRIPTOR);
        ClassNode target = classWith(handle);
        // What Mixin emits for a private, cancellable callback: a call on the target class itself.
        handle.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET,
                "mcaquests$routeGift",
                "(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;"
                        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", false));

        assertTrue(McaGiftMixinPlugin.invokesHandler(target, handle));
    }

    @Test
    @DisplayName("an inlined static call to the handler also reads as applied")
    void inlinedHandlerCallIsFound() {
        MethodNode handle = method("handle", DESCRIPTOR);
        ClassNode target = classWith(handle);
        handle.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                McaGiftMixinPlugin.handlerOwner(), "handle",
                "(Ljava/lang/Object;Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)"
                        + "Ldev/otectus/mcaquests/compat/mca/McaGiftHookEvents$Outcome;", false));

        assertTrue(McaGiftMixinPlugin.invokesHandler(target, handle));
    }

    @Test
    @DisplayName("a method the injection never reached reads as not applied")
    void withoutAnInjectionItIsNotApplied() {
        MethodNode untouched = method("handle", DESCRIPTOR);
        ClassNode target = classWith(untouched);
        untouched.instructions.add(new InsnNode(Opcodes.ICONST_1));
        untouched.instructions.add(new InsnNode(Opcodes.IRETURN));

        assertFalse(McaGiftMixinPlugin.invokesHandler(target, untouched));

        MethodNode somebodyElsesCall = method("handle", DESCRIPTOR);
        ClassNode other = classWith(somebodyElsesCall);
        somebodyElsesCall.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET,
                "someothermod$onCommand", "()V", false));
        somebodyElsesCall.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "some/other/mod/Handler", "onCommand", "()V", false));

        assertFalse(McaGiftMixinPlugin.invokesHandler(other, somebodyElsesCall),
                "another mod injecting into the same method is not evidence that ours did");
    }
}
