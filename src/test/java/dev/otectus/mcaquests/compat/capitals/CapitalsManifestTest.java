package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.compat.CompatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What can be proved about {@link CapitalsBinding}'s manifest with no Capitals jar in the room.
 *
 * <p>{@code CapitalsBindingProbeTest} is the one that checks the members actually exist, and it is
 * skipped unless somebody supplies a jar. These are the invariants that must hold on every ordinary
 * checkout: that the manifest is internally consistent, that resolution against nothing is boring
 * rather than fatal, and that the bridge selection is the decision table it claims to be.
 */
class CapitalsManifestTest {

    @Test
    @DisplayName("every owner is a dotted name relative to the dotted package root")
    void ownersAreDotted() {
        assertFalse(CapitalsBinding.PACKAGE.contains("/"),
                "The package root must stay dotted: a slash-form literal is exactly what "
                        + "NoCapitalsStaticLinkTest scans for, and it may not be exempted.");
        assertTrue(CapitalsBinding.PACKAGE.endsWith("."),
                "The package root is concatenated with an owner, so it must carry its own dot.");
        assertFalse(CapitalsBinding.PROBE_CLASS.contains("/"));

        for (CapitalsBinding.Member member : CapitalsBinding.MANIFEST) {
            assertFalse(member.ownerRelative().contains("/"),
                    member + " names its owner in slash form; Class.forName takes the dotted one.");
            assertFalse(member.ownerRelative().startsWith(CapitalsBinding.PACKAGE),
                    member + " repeats the package root, which would be concatenated twice.");
        }
    }

    @Test
    @DisplayName("no two members share an owner, name, arity and kind")
    void membersAreUnique() {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (CapitalsBinding.Member member : CapitalsBinding.MANIFEST) {
            String key = member.ownerRelative() + "#" + member.memberName() + "/" + member.arity()
                    + ":" + member.kindName();
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        assertEquals(List.of(), duplicates,
                "Members are found by owner, name, arity and staticness alone — never by parameter "
                        + "type — so two entries under one key would bind to whichever the JVM listed "
                        + "first. Duplicates: " + duplicates);
    }

    @Test
    @DisplayName("every capability has at least one required member")
    void everyCapabilityIsDeclared() {
        assertEquals(EnumSet.allOf(CapitalsCapability.class),
                EnumSet.copyOf(CapitalsBinding.DECLARED_CAPABILITIES),
                "A capability whose members are all optional could never be found missing, and would "
                        + "report as bound on a Capitals that has none of it.");
    }

    @Test
    @DisplayName("an absent Capitals resolves to nothing, with working stubs")
    void absentIsBoring() {
        CapitalsBinding.Resolution resolution = CapitalsBinding.absent();

        assertEquals(CompatStatus.ABSENT, resolution.status());
        assertTrue(resolution.capabilities().isEmpty());
        assertTrue(resolution.unresolved().isEmpty(),
                "An absent Capitals is not a partial binding; nothing should be reported as a miss.");
        assertNull(resolution.type(CapitalsBinding.CLASS_NOBLE_TITLE));

        for (CapitalsBinding.Member member : CapitalsBinding.MANIFEST) {
            MethodHandle handle = resolution.handle(member);
            assertNotNull(handle, member + " has no stub; the bridge invokes handles with no null check.");
            int expected = switch (member.kindName()) {
                case "virtual" -> member.arity() + 1; // the receiver comes first
                case "class" -> 0;
                default -> member.arity();
            };
            assertEquals(expected, handle.type().parameterCount(),
                    member + "'s stub has the wrong shape, so invoking it would throw where the real "
                            + "handle would not.");
        }
    }

    @Test
    @DisplayName("an absent Capitals answers every bridge question empty")
    void theBridgeOverAnAbsentResolutionIsEmpty() {
        CapitalsBridge bridge = ReflectiveCapitalsBridge.of(CapitalsBinding.absent());

        assertEquals(CompatStatus.ABSENT, bridge.status());
        assertEquals(CapitalsCapability.values().length, bridge.capabilities().size());
        for (CapitalsCapability capability : CapitalsCapability.values()) {
            assertFalse(bridge.has(capability), capability + " must not report bound with nothing bound.");
        }
        assertTrue(bridge.allCapitals().isEmpty());
        assertTrue(bridge.capitalOfResident(java.util.UUID.randomUUID()).isEmpty());
        assertFalse(bridge.isActive(new CapitalRef(new Object(), java.util.UUID.randomUUID(), 1, "")));
    }

    @Test
    @DisplayName("select() is the whole decision: absent, disabled, or whatever bound")
    void selectDecisionTable() {
        assertEquals(CompatStatus.ABSENT,
                CapitalsCompat.select(false, true, CapitalsBinding.absent()).status(),
                "Capitals not installed is the silent path, never a warning and never DISABLED.");
        assertEquals(CompatStatus.DISABLED,
                CapitalsCompat.select(true, false, CapitalsBinding.absent()).status(),
                "Installed but switched off must say so, or the owner who switched it off goes "
                        + "looking for a missing jar.");
        assertEquals(CompatStatus.ABSENT,
                CapitalsCompat.select(true, true, CapitalsBinding.absent()).status(),
                "Enabled with nothing resolved still reports what the resolution says.");
    }
}
