package dev.otectus.mcaquests.quest.target;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.capitals.CapitalRole;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code {"mode": "capital_role"}} — the MCA Capitals villager selector.
 *
 * <p>The mode adds a sixth component to a record datapacks and add-ons both write, so the cases below
 * cover the two halves of that: JSON without a {@code role} must still parse exactly as it did before
 * 1.6.0, and a {@code capital_role} target without one must be caught at reload rather than resolving
 * to nobody in front of a player.
 */
class VillagerTargetCapitalRoleTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static VillagerTarget parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        DataResult<VillagerTarget> result = VillagerTarget.CODEC.parse(JsonOps.INSTANCE, element);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected " + json + " to parse, but got: " + result.error().orElseThrow().message()));
    }

    private static List<String> validationErrors(VillagerTarget target) {
        List<String> errors = new ArrayList<>();
        target.validate("test", errors);
        return errors;
    }

    @Test
    @DisplayName("a capital_role target round-trips through JSON")
    void roundTrips() {
        VillagerTarget target = parse("{\"mode\":\"capital_role\",\"role\":\"sovereign\"}");

        assertEquals(VillagerTarget.Mode.CAPITAL_ROLE, target.mode());
        assertEquals(Optional.of(CapitalRole.SOVEREIGN), target.role());

        JsonElement encoded = VillagerTarget.CODEC.encodeStart(JsonOps.INSTANCE, target).result().orElseThrow();
        assertEquals(target, VillagerTarget.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow(),
                "a datapack value must survive being written back out and re-read");
    }

    @Test
    @DisplayName("targets written before the mode existed still parse, with no role")
    void legacyJsonHasNoRole() {
        VillagerTarget family = parse("{\"mode\":\"family\",\"relation\":\"sibling\",\"require\":\"reachable\"}");

        assertEquals(Optional.empty(), family.role());
        assertEquals(VillagerTarget.SELF, parse("{\"mode\":\"self\"}"),
                "the constant every objective defaults to must not have changed shape");
        assertTrue(validationErrors(family).isEmpty());
    }

    @Test
    @DisplayName("capital_role without a role is a reload error, not a silent no-target")
    void roleIsRequired() {
        List<String> errors = validationErrors(parse("{\"mode\":\"capital_role\"}"));

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("'role'"), errors.get(0));
    }

    @Test
    @DisplayName("a rank no villager can hold is rejected, since it could only ever resolve to nobody")
    void playerOnlyRoleRejected() {
        List<String> errors = validationErrors(parse("{\"mode\":\"capital_role\",\"role\":\"archduke\"}"));

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("archduke"), errors.get(0));
        assertTrue(validationErrors(parse("{\"mode\":\"capital_role\",\"role\":\"herald\"}")).isEmpty(),
                "an office villagers do hold must validate clean");
    }

    @Test
    @DisplayName("'require' belongs to family mode alone, capital_role included")
    void requireRejectedOnCapitalRole() {
        List<String> errors = validationErrors(
                parse("{\"mode\":\"capital_role\",\"role\":\"heir\",\"require\":\"reachable\"}"));

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("'require'"), errors.get(0));
    }

    @Test
    @DisplayName("the label is the office's own name, and 'any' still means the whole court")
    void describesTheOffice() {
        assertEquals("mcaquests.target.capital_role.hand", key(parse(
                "{\"mode\":\"capital_role\",\"role\":\"hand\"}").describe()));
        assertEquals("mcaquests.target.capital_role.member", key(parse(
                "{\"mode\":\"capital_role\",\"role\":\"any\"}").describe()),
                "'any' is the spelling a pack reaches for; it must land on the member label");
    }

    @Test
    @DisplayName("a target with no giver names nobody, rather than throwing")
    void noGiverNoHolders() {
        assertTrue(parse("{\"mode\":\"capital_role\",\"role\":\"sovereign\"}")
                .capitalRoleHolders(null, null).isEmpty());
        assertTrue(VillagerTarget.SELF.capitalRoleHolders(null, null).isEmpty(),
                "another mode never asks Capitals anything");
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }
}
