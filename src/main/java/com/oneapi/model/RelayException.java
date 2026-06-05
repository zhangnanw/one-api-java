package com.oneapi.model;

/**
 * Wraps a {@link RelayError} so it can be used in Vert.x {@code Future.failedFuture()}.
 * Since {@code RelayError} is a sealed interface (not a {@code Throwable}),
 * this exception acts as the bridge.
 */
public class RelayException extends RuntimeException {
    private final RelayError error;

    public RelayException(RelayError error) {
        super(error.toString());
        this.error = error;
    }

    public RelayError getError() {
        return error;
    }
}
