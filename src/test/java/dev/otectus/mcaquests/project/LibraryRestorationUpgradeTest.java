package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.project.objective.ProjectTalkObjective;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Library Restoration saved mid-{@code catalogue} by 1.6.5 and loaded by this version (brief C06,
 * TALK-05): the librarians already counted stay counted, a third distinct librarian finishes the phase,
 * and talking to one of the first two again adds nothing.
 */
class LibraryRestorationUpgradeTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("TALK-05: a partially counted catalogue survives the upgrade and completes on the third librarian")
    void partialCatalogueSurvivesUpgrade() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ProjectState old = new ProjectState(new ResourceLocation("mcaquests", "library_restoration"),
                ProjectScope.PROFESSION, "p:1:minecraft:librarian", new ResourceLocation("minecraft", "overworld"),
                BlockPos.ZERO, OptionalInt.of(1), 0L, 1);
        old.progress(0).markTalkedTo(first);
        old.progress(0).markTalkedTo(second);
        old.progress(0).add(2);
        CompoundTag saved = old.save();
        assertFalse(saved.contains("anchor_radius"), "the fixture has the 1.6.5 shape");

        ProjectState loaded = ProjectState.load(saved);
        SharedObjectiveProgress catalogue = loaded.progress(0);
        ProjectTalkObjective librarians = new ProjectTalkObjective(new ResourceLocation("minecraft", "librarian"), 3);
        assertEquals(2, catalogue.count());
        assertTrue(catalogue.hasTalkedTo(first) && catalogue.hasTalkedTo(second));
        assertFalse(catalogue.markTalkedTo(first), "a librarian already counted is not counted again");
        assertTrue(catalogue.markTalkedTo(UUID.randomUUID()));
        catalogue.add(1);
        assertTrue(librarians.isSatisfied(catalogue));
    }

    @Test
    @DisplayName("a resident of the bound village counts wherever the conversation happens")
    void residentCountsAnywhere() {
        ProjectState state = new ProjectState(new ResourceLocation("mcaquests", "library_restoration"),
                ProjectScope.PROFESSION, "p:7:minecraft:librarian", new ResourceLocation("minecraft", "overworld"),
                BlockPos.ZERO, OptionalInt.of(7), 0L, 1);
        // A resident short-circuits before any position test, so no level is needed to decide it.
        assertTrue(ProjectManager.talkCounts(state, OptionalInt.of(7), null));
    }
}
