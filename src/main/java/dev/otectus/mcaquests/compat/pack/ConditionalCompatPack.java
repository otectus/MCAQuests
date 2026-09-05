package dev.otectus.mcaquests.compat.pack;

import dev.otectus.mcaquests.compat.CompatRegistry;

import java.util.function.Predicate;

/**
 * One datapack that ships inside the jar and is mounted only when the mod it is written for is
 * actually there.
 *
 * <p>The alternative — shipping the content unconditionally and filtering it after the fact, the way
 * {@code BuiltinPack} handles {@code enableDefaultQuestPack} — does not work for compatibility
 * content. A quest naming an Ice &amp; Fire dragon cannot even be parsed on an install without the
 * mod, and a data file meant for a third-party loader would be read by that loader whether we wanted
 * it or not. Not mounting the pack at all is the only form of "off" that is genuinely off.
 *
 * @param id          the pack id, shown as {@code mcaquests/<id>} in {@code /datapack list} and used
 *                    to build its description key {@code mcaquests.compatpack.<id>}
 * @param folder      the sub-directory of {@code compatpacks/} in the mod jar that holds it
 * @param requirement what must be true of the {@link CompatRegistry} for this pack to be mounted,
 *                    evaluated fresh every time the pack repository is built
 */
public record ConditionalCompatPack(String id, String folder, Predicate<CompatRegistry> requirement) {

    /** True when this pack's content is usable on this installation, right now. */
    public boolean isEnabled(CompatRegistry registry) {
        return requirement.test(registry);
    }
}
