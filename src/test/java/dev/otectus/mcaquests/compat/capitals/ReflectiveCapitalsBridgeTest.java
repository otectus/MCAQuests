package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises failing third-party handles through the production bridge, without loading Capitals. */
class ReflectiveCapitalsBridgeTest {
    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final UUID VILLAGER = UUID.randomUUID();
    private static final CapitalRef CAPITAL = new CapitalRef(new Object(), UUID.randomUUID(), 7,
            "minecraft:overworld");

    @Test
    void requiredRuntimeFailureDisablesOnlyItsCapabilityUntilReprobe() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CapitalsBinding.Resolution resolution = resolution(Map.of(CapitalsBinding.REC_STATE,
                throwing(CapitalsBinding.REC_STATE, calls)));
        CapitalsBridge bridge = ReflectiveCapitalsBridge.of(resolution);

        assertEquals(CompatStatus.FULL, bridge.status());
        assertFalse(bridge.isActive(CAPITAL));
        assertFalse(bridge.isActive(CAPITAL));
        assertEquals(1, calls.get(), "broken methods must not be invoked every offer/tick");
        assertFalse(bridge.has(CapitalsCapability.REGISTRY));
        assertTrue(bridge.has(CapitalsCapability.ROLES));
        assertEquals(CompatStatus.PARTIAL, bridge.status());
        assertEquals(List.of(CapitalsBinding.REC_STATE.toString()), bridge.unresolvedMembers());
        assertFalse(bridge.capabilities().stream()
                .filter(capability -> capability.id().equals(CapitalsCapability.REGISTRY.id()))
                .findFirst().orElseThrow().present());
        assertTrue(ReflectiveCapitalsBridge.of(resolution).has(CapitalsCapability.REGISTRY),
                "a fresh probe may recover a failed dependency");
    }

    @Test
    void failedGenderReadIsUnknownAndDoesNotDisableTitleRewards() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CapitalsBridge bridge = bridge(Map.of(CapitalsBinding.VILLAGER_IS_FEMALE,
                throwing(CapitalsBinding.VILLAGER_IS_FEMALE, calls)));
        ServerLevel level = unusedLevel();

        assertTrue(bridge.isVillagerFemale(level, VILLAGER).isEmpty());
        assertTrue(bridge.isVillagerFemale(level, VILLAGER).isEmpty());
        assertEquals(1, calls.get());
        assertTrue(bridge.has(CapitalsCapability.VILLAGER_TITLES));
        assertEquals(CompatStatus.FULL, bridge.status());
    }

    @Test
    void failedPersistenceNeverReportsRewardSuccessAndDisablesBothRecordMutations() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        CapitalsBridge bridge = bridge(Map.of(
                CapitalsBinding.REC_ADD_KNIGHT, counting(CapitalsBinding.REC_ADD_KNIGHT, writes),
                CapitalsBinding.MARK_DIRTY, throwing(CapitalsBinding.MARK_DIRTY, saves)));
        ServerLevel level = unusedLevel();

        assertFalse(bridge.setVillagerTitle(level, CAPITAL, VILLAGER, "DAME"));
        assertFalse(bridge.setVillagerTitle(level, CAPITAL, VILLAGER, "DAME"));
        assertFalse(bridge.addChronicleEntry(level, CAPITAL, "An accolade was earned.", false));
        assertEquals(1, writes.get());
        assertEquals(1, saves.get());
        assertFalse(bridge.has(CapitalsCapability.VILLAGER_TITLES));
        assertFalse(bridge.has(CapitalsCapability.CHRONICLE));
        assertTrue(bridge.has(CapitalsCapability.REGISTRY));
        assertTrue(bridge.has(CapitalsCapability.TITLE_GRANTS));
    }

    @Test
    void successfulRecordMutationsMarkSavedDataDirty() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        CapitalsBridge bridge = bridge(Map.of(CapitalsBinding.MARK_DIRTY,
                counting(CapitalsBinding.MARK_DIRTY, saves)));
        ServerLevel level = unusedLevel();

        assertTrue(bridge.setVillagerTitle(level, CAPITAL, VILLAGER, "LADY"));
        assertTrue(bridge.addChronicleEntry(level, CAPITAL, "The court welcomed a lady.", false));
        assertTrue(bridge.addChronicleEntry(level, CAPITAL, "The court announced the title.", true));
        assertEquals(3, saves.get());
    }

    @Test
    void memberTargetsIncludeTheSameFormerCourtMembersAsConditions() throws Exception {
        List<CapitalsBinding.Member> collections = List.of(CapitalsBinding.REC_HOUSEHOLD,
                CapitalsBinding.REC_DISINHERITED, CapitalsBinding.REC_LEGITIMIZED,
                CapitalsBinding.REC_DISGRACED_GUARDS);
        List<CapitalsBinding.Member> predicates = List.of(CapitalsBinding.REC_IS_HOUSEHOLD,
                CapitalsBinding.REC_IS_DISINHERITED, CapitalsBinding.REC_IS_LEGITIMIZED,
                CapitalsBinding.REC_IS_DISGRACED_GUARD);
        ServerLevel level = unusedLevel();
        for (int i = 0; i < collections.size(); i++) {
            CapitalsBridge bridge = bridge(Map.of(
                    collections.get(i), constant(collections.get(i), Set.of(VILLAGER)),
                    predicates.get(i), constant(predicates.get(i), true)));
            assertTrue(bridge.villagerHasRole(level, CAPITAL, VILLAGER, CapitalRole.MEMBER));
            assertEquals(List.of(VILLAGER), bridge.villagerRoleHolders(level, CAPITAL, CapitalRole.MEMBER));
        }
    }

    @Test
    void snapshotsDistinguishNoVacanciesFromAFailedRead() throws Exception {
        ServerLevel level = unusedLevel();
        CapitalsBridge healthy = bridge(Map.of(CapitalsBinding.INTERREGNUM_SNAPSHOT,
                constant(CapitalsBinding.INTERREGNUM_SNAPSHOT, Map.of())));
        assertEquals(Map.of(), healthy.interregnumSnapshot(level).orElseThrow());

        CapitalsBridge broken = bridge(Map.of(CapitalsBinding.INTERREGNUM_SNAPSHOT,
                throwing(CapitalsBinding.INTERREGNUM_SNAPSHOT, new AtomicInteger())));
        assertTrue(broken.interregnumSnapshot(level).isEmpty());
        assertFalse(broken.has(CapitalsCapability.INTERREGNUM));
    }

    @Test
    void snapshotCannotPublishFallbackMetadataAfterARecordAccessorFails() throws Exception {
        CapitalsBridge bridge = bridge(Map.of(
                CapitalsBinding.INTERREGNUM_SNAPSHOT,
                constant(CapitalsBinding.INTERREGNUM_SNAPSHOT, Map.of(CAPITAL.capitalId(), new Object())),
                CapitalsBinding.IR_WAS_PLAYER, throwing(CapitalsBinding.IR_WAS_PLAYER, new AtomicInteger())));

        assertTrue(bridge.interregnumSnapshot(unusedLevel()).isEmpty());
        assertFalse(bridge.has(CapitalsCapability.INTERREGNUM));
    }

    @Test
    void vacantPlayerThroneUsesTheFormerPlayerIdWhenTheDeceasedFieldIsEmpty() throws Exception {
        CapitalsBridge bridge = bridge(Map.of(
                CapitalsBinding.INTERREGNUM_SNAPSHOT,
                constant(CapitalsBinding.INTERREGNUM_SNAPSHOT, Map.of(CAPITAL.capitalId(), new Object())),
                CapitalsBinding.IR_WAS_PLAYER, constant(CapitalsBinding.IR_WAS_PLAYER, true),
                CapitalsBinding.IR_FORMER_PLAYER, constant(CapitalsBinding.IR_FORMER_PLAYER, VILLAGER)));

        InterregnumView vacancy = bridge.interregnumSnapshot(unusedLevel()).orElseThrow().get(CAPITAL.capitalId());
        assertEquals(VILLAGER, vacancy.deceasedSovereign());
        assertTrue(vacancy.wasPlayerSovereign());
    }

    private static CapitalsBridge bridge(Map<CapitalsBinding.Member, MethodHandle> overrides) throws Exception {
        return ReflectiveCapitalsBridge.of(resolution(overrides));
    }

    /** Keep production resolution immutable; only this fixture replaces its handles. */
    private static CapitalsBinding.Resolution resolution(Map<CapitalsBinding.Member, MethodHandle> overrides)
            throws Exception {
        Map<CapitalsBinding.Member, MethodHandle> handles = new IdentityHashMap<>();
        for (CapitalsBinding.Member member : CapitalsBinding.MANIFEST) {
            handles.put(member, MethodHandles.empty(member.erasedType()));
        }
        handles.putAll(overrides);
        Constructor<CapitalsBinding.Resolution> constructor = CapitalsBinding.Resolution.class
                .getDeclaredConstructor(CompatStatus.class, Set.class, Map.class, Map.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(CompatStatus.FULL, EnumSet.allOf(CapitalsCapability.class),
                handles, Map.of(), List.of());
    }

    private static MethodHandle constant(CapitalsBinding.Member member, Object value) {
        return MethodHandles.dropArguments(MethodHandles.constant(member.erasedType().returnType(), value),
                0, member.erasedType().parameterList());
    }

    private static MethodHandle throwing(CapitalsBinding.Member member, AtomicInteger calls) throws Exception {
        MethodHandle failure = MethodHandles.lookup().findStatic(ReflectiveCapitalsBridgeTest.class,
                "fail", MethodType.methodType(Object.class, AtomicInteger.class)).bindTo(calls);
        return MethodHandles.dropArguments(failure.asType(MethodType.methodType(member.erasedType().returnType())),
                0, member.erasedType().parameterList());
    }

    private static Object fail(AtomicInteger calls) {
        calls.incrementAndGet();
        throw new NoSuchMethodError("simulated Capitals dependency drift");
    }

    private static MethodHandle counting(CapitalsBinding.Member member, AtomicInteger calls) throws Exception {
        MethodHandle increment = MethodHandles.lookup().findVirtual(AtomicInteger.class, "incrementAndGet",
                MethodType.methodType(int.class)).bindTo(calls).asType(MethodType.methodType(void.class));
        return MethodHandles.dropArguments(increment, 0, member.erasedType().parameterList());
    }

    /** Only passed through to erased fixture handles; no world methods or fields are accessed. */
    private static ServerLevel unusedLevel() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (ServerLevel) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), ServerLevel.class);
    }
}
