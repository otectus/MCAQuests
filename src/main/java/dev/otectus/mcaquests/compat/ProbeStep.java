package dev.otectus.mcaquests.compat;

import java.util.Optional;

/**
 * One step of a backend's self-test, as data rather than as a sentence.
 *
 * <p>The old probe returned English, from a server command, which made it untranslatable and
 * unassertable at once. {@code name} is a stable identifier the client command turns into a
 * translation key; {@code detail} carries only what a translation cannot supply — a count, a member
 * that did not bind, the type of an exception.
 *
 * @param name   stable, lowercase, no spaces: {@code session}, {@code store}, {@code add}
 * @param passed whether the step did what it set out to do
 */
public record ProbeStep(String name, boolean passed, Optional<String> detail) {

    public static ProbeStep passed(String name) {
        return new ProbeStep(name, true, Optional.empty());
    }

    public static ProbeStep passed(String name, String detail) {
        return new ProbeStep(name, true, Optional.of(detail));
    }

    public static ProbeStep failed(String name, String detail) {
        return new ProbeStep(name, false, Optional.of(detail));
    }
}
