package com.dataguard.exception;

/**
 * Thrown when an uploaded ZIP archive fails any of DataGuard's security or
 * size validation checks. The message is user-safe and does not expose
 * internal server paths.
 */
public class ZipValidationException extends Exception {

    public ZipValidationException(String message) {
        super(message);
    }

    public ZipValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
