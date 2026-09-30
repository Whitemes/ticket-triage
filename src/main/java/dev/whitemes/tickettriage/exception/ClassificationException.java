package dev.whitemes.tickettriage.exception;

/** Thrown when the model's reply cannot be turned into a valid classification result. */
public class ClassificationException extends RuntimeException {

    public ClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
