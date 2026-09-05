package dev.otectus.mcaquests.compat.bountiful;

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
 * The two decisions {@code BountifulMixinPlugin} makes, asserted against a synthetic class rather
 * than against a transformed copy of somebody else's mod.
 *
 * <p>Both injections carry {@code require = 0}, which means an {@code @At} that matched nothing is
 * not an error — so the only thing standing between a hook that silently observes nothing and a bug
 * report saying "bounty quests never advance" is the post-apply scan. Nothing else in this
 * integration can be checked without Bountiful actually installed, and this can.
 */
class BountifulMixinPluginTest {

    private static final String DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z";

    private static ClassNode classWith(MethodNode... methods) {
        ClassNode node = new ClassNode();
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
        assertNotNull(BountifulMixinPlugin.findCashIn(classWith(method("tryCashIn", DESCRIPTOR))));
        assertNull(BountifulMixinPlugin.findCashIn(classWith(method("tryCashIn", "()Z"))),
                "a tryCashIn taking something else is not the method the hook can observe, and "
                        + "applying to it would report completions that never happened");
        assertNull(BountifulMixinPlugin.findCashIn(classWith(method("cashIn", DESCRIPTOR))));
        assertNull(BountifulMixinPlugin.findCashIn(new ClassNode()), "no methods at all is not a crash");
    }

    @Test
    @DisplayName("a call to our handler reads as applied")
    void handlerCallIsFound() {
        MethodNode cashIn = method("tryCashIn", DESCRIPTOR);
        cashIn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                BountifulMixinPlugin.handlerOwner(), "beforeCashIn", "()V", false));

        assertTrue(BountifulMixinPlugin.invokesHandler(cashIn));
    }

    @Test
    @DisplayName("a method the injection never reached reads as not applied")
    void withoutTheHandlerCallItIsNotApplied() {
        MethodNode untouched = method("tryCashIn", DESCRIPTOR);
        untouched.instructions.add(new InsnNode(Opcodes.ICONST_1));
        untouched.instructions.add(new InsnNode(Opcodes.IRETURN));

        assertFalse(BountifulMixinPlugin.invokesHandler(untouched));

        MethodNode somebodyElsesCall = method("tryCashIn", DESCRIPTOR);
        somebodyElsesCall.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "some/other/mod/Handler", "onCashIn", "()V", false));
        assertFalse(BountifulMixinPlugin.invokesHandler(somebodyElsesCall),
                "another mod hooking the same method is not evidence that ours did");
    }
}
