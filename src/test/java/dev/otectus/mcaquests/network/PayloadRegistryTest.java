package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestPaths;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Spec §14.5: all 21 payload ids are the ones §14.2 names, all 21 are unique, all 21 are registered
 * in the right direction, and the protocol is 15.
 *
 * <p>None of that can be asserted by calling {@code QuestNetwork.onRegisterPayloads} with a recording
 * double, because {@code RegisterPayloadHandlersEvent} and {@code PayloadRegistrar} are final and
 * built around a live {@code ModContainer} — a stub would be a re-implementation of NeoForge, which is
 * not what needs testing. So the registration is checked from two sides that together pin it down:
 * reflection over the payload classes themselves (what is declared), and a read of
 * {@code QuestNetwork.java} (what is registered, and in which direction). A packet added without a
 * registration, or registered on the wrong side, fails one or the other.
 *
 * <p>The id map below is hard-coded from spec §14.2 on purpose. Reading the ids back off the classes
 * and comparing them to themselves would pass no matter what they said; the point is that a rename
 * breaks a documented wire contract, and the spec is the thing it must not drift from.
 */
class PayloadRegistryTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final String PACKAGE = "dev.otectus.mcaquests.network.";
    private static final String CLIENT_HANDLERS = PACKAGE + "ClientPayloadHandlers";

    /** Spec §14.2, verbatim: payload class to payload id, and which way it travels. */
    private static final Map<String, String> C2S = new LinkedHashMap<>();
    private static final Map<String, String> S2C = new LinkedHashMap<>();

    static {
        C2S.put("OpenQuestMenuC2SPacket", "open_quest_menu");
        C2S.put("QuestDecisionC2SPacket", "quest_decision");
        C2S.put("QuestTurnInC2SPacket", "quest_turn_in");
        C2S.put("QuestAbandonC2SPacket", "quest_abandon");
        C2S.put("ProjectContributeC2SPacket", "project_contribute");
        C2S.put("RequestJournalC2SPacket", "request_journal");
        C2S.put("QuestAbandonFromLogC2SPacket", "quest_abandon_from_log");
        C2S.put("OpenStandingC2SPacket", "open_standing");
        C2S.put("QuestTrackC2SPacket", "quest_track");

        S2C.put("QuestMenuDataS2CPacket", "quest_menu_data");
        S2C.put("QuestLogSyncS2CPacket", "quest_log_sync");
        S2C.put("QuestReadyToastS2CPacket", "quest_ready_toast");
        S2C.put("ProjectMenuDataS2CPacket", "project_menu_data");
        S2C.put("ProjectLogSyncS2CPacket", "project_log_sync");
        S2C.put("ProjectPhaseToastS2CPacket", "project_phase_toast");
        S2C.put("ReputationTierToastS2CPacket", "reputation_tier_toast");
        S2C.put("JournalSyncS2CPacket", "journal_sync");
        S2C.put("SituationToastS2CPacket", "situation_toast");
        S2C.put("FtbqEditorIdsS2CPacket", "ftbq_editor_ids");
        S2C.put("HighlightTargetsS2CPacket", "highlight_targets");
        S2C.put("QuestGuidanceS2CPacket", "quest_guidance");
    }

    private static List<String> allPayloads() {
        List<String> all = new ArrayList<>(C2S.keySet());
        all.addAll(S2C.keySet());
        return all;
    }

    private static Class<?> payloadClass(String simpleName) {
        try {
            return Class.forName(PACKAGE + simpleName);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("spec §14.2 names " + simpleName + ", which does not exist", e);
        }
    }

    private static CustomPacketPayload.Type<?> type(String simpleName) {
        try {
            Field field = payloadClass(simpleName).getDeclaredField("TYPE");
            assertTrue(Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers()), simpleName + ".TYPE must be public static final");
            assertEquals(CustomPacketPayload.Type.class, field.getType(),
                    simpleName + ".TYPE must be a CustomPacketPayload.Type");
            Object value = field.get(null);
            assertNotNull(value, simpleName + ".TYPE must not be null");
            return (CustomPacketPayload.Type<?>) value;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(simpleName + " has no TYPE constant", e);
        }
    }

    private static String questNetworkSource() {
        Path source = TestPaths.of("src", "main", "java", "dev", "otectus", "mcaquests", "network",
                "QuestNetwork.java");
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + source, e);
        }
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- ids

    @Test
    @DisplayName("all 21 payloads carry the id spec §14.2 gives them")
    void everyPayloadHasItsSpecifiedId() {
        assertEquals(21, allPayloads().size(), "spec §14.2 lists exactly 21 payloads");
        for (String simpleName : allPayloads()) {
            String expected = C2S.containsKey(simpleName) ? C2S.get(simpleName) : S2C.get(simpleName);
            assertEquals(ResourceLocation.fromNamespaceAndPath("mcaquests", expected),
                    type(simpleName).id(), simpleName + " must keep its §14.2 payload id");
        }
    }

    @Test
    @DisplayName("no two payloads share an id")
    void payloadIdsAreUnique() {
        // A collision does not fail at registration in any way a player would understand: two payloads
        // under one id means one of them is quietly decoded as the other.
        Set<String> seen = new TreeSet<>();
        for (String simpleName : allPayloads()) {
            String id = type(simpleName).id().toString();
            assertTrue(seen.add(id), "duplicate payload id " + id + " (at " + simpleName + ")");
        }
        assertEquals(21, seen.size());
    }

    // ---------------------------------------------------------------- direction

    @Test
    @DisplayName("every C2S payload has a server handler, and no S2C payload does")
    void directionIsExpressedByWhereTheHandlerLives() {
        for (String simpleName : C2S.keySet()) {
            assertTrue(hasServerHandler(payloadClass(simpleName)),
                    simpleName + " travels to the server and must declare static handle(msg, IPayloadContext)");
        }
        for (String simpleName : S2C.keySet()) {
            // An S2C payload with a server-side handler is a payload the server would act on if a
            // client sent it -- the shape §14.5 exists to rule out.
            assertFalse(hasServerHandler(payloadClass(simpleName)),
                    simpleName + " travels to the client; its handler belongs in ClientPayloadHandlers");
        }
    }

    private static boolean hasServerHandler(Class<?> payload) {
        for (Method method : payload.getDeclaredMethods()) {
            if (!method.getName().equals("handle") || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2 && parameters[0].equals(payload)
                    && parameters[1].equals(IPayloadContext.class)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("every S2C payload has a handler in the client bridge")
    void everyClientPayloadHasABridgeMethod() {
        // Loaded with initialize=false: ClientPayloadHandlers reaches Minecraft client classes, and
        // running its static initializer here would be doing on the test worker exactly what the
        // dedicated server must never do. Resolving its declared methods does not initialize it.
        Class<?> bridge;
        try {
            bridge = Class.forName(CLIENT_HANDLERS, false, PayloadRegistryTest.class.getClassLoader());
        } catch (Throwable t) {
            fail("could not reflect over " + CLIENT_HANDLERS + " without initializing it: " + t);
            return;
        }

        Set<String> handled = new TreeSet<>();
        for (Method method : bridge.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) && parameters.length == 2
                    && parameters[1].equals(IPayloadContext.class)) {
                handled.add(parameters[0].getSimpleName());
            }
        }
        for (String simpleName : S2C.keySet()) {
            assertTrue(handled.contains(simpleName),
                    CLIENT_HANDLERS + " has no static handler taking " + simpleName);
        }
        assertEquals(S2C.size(), handled.size(),
                "the client bridge must handle the 12 S2C payloads and nothing else");
    }

    // ---------------------------------------------------------------- protocol and registration

    @Test
    @DisplayName("the protocol version is 15")
    void protocolVersionIs15() {
        // Read reflectively rather than exposed: the constant is deliberately private, and a test is
        // not a reason to widen it. A mismatch here is a client that can silently join a server
        // speaking a different wire format.
        try {
            Field field = QuestNetwork.class.getDeclaredField("PROTOCOL_VERSION");
            field.setAccessible(true);
            assertEquals("15", field.get(null), "spec §14.1 fixes the NeoForge protocol at 15");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("QuestNetwork must keep a PROTOCOL_VERSION constant", e);
        }
    }

    @Test
    @DisplayName("QuestNetwork registers 9 payloads to the server and 12 to the client")
    void registrationCountsMatchTheDirections() {
        String source = questNetworkSource();
        assertEquals(C2S.size(), countOf(source, "playToServer("),
                "one playToServer call per C2S payload, no more and no fewer");
        assertEquals(S2C.size(), countOf(source, "playToClient("),
                "one playToClient call per S2C payload, no more and no fewer");
    }

    @Test
    @DisplayName("QuestNetwork registers every payload exactly once")
    void everyPayloadIsRegisteredExactlyOnce() {
        // The failure this catches is a payload class that exists, round-trips, and is never
        // registered -- which behaves as "that feature silently does nothing" rather than as an error.
        String source = questNetworkSource();
        for (String simpleName : allPayloads()) {
            assertEquals(1, countOf(source, simpleName + ".TYPE"),
                    simpleName + " must be registered in QuestNetwork exactly once");
        }
    }
}
