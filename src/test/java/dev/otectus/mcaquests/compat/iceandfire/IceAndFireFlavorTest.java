package dev.otectus.mcaquests.compat.iceandfire;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The four outcomes of the class probe, exercised without a class loader.
 *
 * <p>{@link IceAndFireFlavor#detect} takes a predicate precisely so this can be a plain unit test:
 * the case that matters most — both builds present at once — cannot be produced in a real JVM
 * without shipping two mods that refuse to load together.
 */
class IceAndFireFlavorTest {

    private static Predicate<String> present(String... classNames) {
        Set<String> found = Set.of(classNames);
        return found::contains;
    }

    @Test
    void noEntryPointIsNone() {
        assertEquals(IceAndFireFlavor.NONE, IceAndFireFlavor.detect(present()));
    }

    @Test
    void communityEditionClassIsCommunityEdition() {
        assertEquals(IceAndFireFlavor.COMMUNITY_EDITION,
                IceAndFireFlavor.detect(present(IceAndFireFlavor.CE_CLASS)));
    }

    @Test
    void originalClassIsOriginal() {
        assertEquals(IceAndFireFlavor.ORIGINAL,
                IceAndFireFlavor.detect(present(IceAndFireFlavor.ORIGINAL_CLASS)));
    }

    @Test
    void bothClassesAreAmbiguous() {
        assertEquals(IceAndFireFlavor.AMBIGUOUS,
                IceAndFireFlavor.detect(present(IceAndFireFlavor.CE_CLASS, IceAndFireFlavor.ORIGINAL_CLASS)));
    }
}
