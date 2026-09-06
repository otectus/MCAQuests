package dev.otectus.mcaquests.compat.capitals;

import java.util.UUID;

/**
 * One capital, as everything outside {@code compat.capitals} is allowed to hold it.
 *
 * <p>{@code record} is Capitals' own {@code CapitalRecord}, carried as an {@link Object} so no caller
 * ever names the type — the handle the bridge hands out and takes straight back. <b>Nothing outside
 * this package may unwrap it.</b> The three fields beside it are copied at hand-out time so a caller
 * can log, compare and route on a capital without a second reflective call.
 *
 * @param record       Capitals' {@code CapitalRecord}, opaque
 * @param capitalId    the capital's own id
 * @param villageId    the MCA village this capital is seated in
 * @param dimensionId  that village's dimension, e.g. {@code "minecraft:overworld"}
 */
public record CapitalRef(Object record, UUID capitalId, int villageId, String dimensionId) {
}
