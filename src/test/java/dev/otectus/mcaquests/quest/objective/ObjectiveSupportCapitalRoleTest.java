package dev.otectus.mcaquests.quest.objective;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ObjectiveSupportCapitalRoleTest {

    @Test
    void unloadedOfficeholderIsPersistedBeforeEntityResolutionAndSurvivesSuccession() {
        ObjectiveProgress progress = new ObjectiveProgress();
        UUID sovereign = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals(Optional.of(sovereign),
                ObjectiveSupport.bindSelectedTarget(progress, () -> Optional.of(sovereign)));
        // No entity was loaded, but the saved quest must already remember this person's identity.
        ObjectiveProgress restored = ObjectiveProgress.load(progress.save());
        assertEquals(sovereign, restored.targetUuid());
        assertEquals(Optional.of(sovereign), ObjectiveSupport.bindSelectedTarget(restored,
                () -> fail("A restored quest must not select a replacement officeholder")));
    }

    @Test
    void vacantOfficeCanRecoverWhenSomeoneIsAppointed() {
        ObjectiveProgress progress = new ObjectiveProgress();
        assertTrue(ObjectiveSupport.bindSelectedTarget(progress, Optional::empty).isEmpty());
        assertNull(progress.targetUuid());

        UUID appointed = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(Optional.of(appointed),
                ObjectiveSupport.bindSelectedTarget(progress, () -> Optional.of(appointed)));
        assertEquals(appointed, progress.targetUuid());
    }
}
