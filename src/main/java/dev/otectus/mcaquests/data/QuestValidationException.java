package dev.otectus.mcaquests.data;

/** Thrown to fail a datapack reload when {@code strictJsonValidation} is enabled (spec section 11). */
public class QuestValidationException extends RuntimeException {

    public QuestValidationException(String message) {
        super(message);
    }
}
