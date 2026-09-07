package dev.otectus.mcaquests.project.data;

import dev.otectus.mcaquests.project.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ProjectLinkValidationTest {
    static { dev.otectus.mcaquests.support.TestBootstrap.ensureBootstrapped(); }
    private static ProjectDefinition project(String id, ProjectScope scope) {
        return new ProjectDefinition(ResourceLocation.fromNamespaceAndPath("test", id), true, 1, Optional.empty(), Optional.empty(),
                new ProjectScopeSpec(scope, List.of(), Optional.empty()), SponsorSpec.ANY,
                List.of(), Optional.empty(), ReputationSpec.NONE, Optional.empty(), Optional.empty());
    }

    @Test void linkedProjectScopeMustKeepTheStoredIdentityMeaning() {
        ProjectDefinition source = project("source", ProjectScope.VILLAGE);
        ProjectDefinition target = project("target", ProjectScope.PLAYER);
        List<String> errors = new ArrayList<>();
        ProjectValidator.validateLink(source, target.id(), "follow_up", Map.of(target.id(), target), errors);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("incompatible scope"));
        target = project("target", ProjectScope.VILLAGE);
        errors.clear();
        ProjectValidator.validateLink(source, target.id(), "unlock", Map.of(target.id(), target), errors);
        assertTrue(errors.isEmpty());
    }

    @Test void missingUnlockTargetIsReportedBeforeThePlayerEarnsIt() {
        ProjectDefinition source = project("source", ProjectScope.VILLAGE);
        List<String> errors = new ArrayList<>();
        ProjectValidator.validateLink(source, ResourceLocation.fromNamespaceAndPath("test", "missing"), "unlock", Map.of(), errors);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("unknown project"));
    }
}
