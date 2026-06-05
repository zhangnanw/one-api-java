package com.oneapi.model;

/**
 * 包装 {@link RelayError}，使其可用于 Vert.x 的 {@code Future.failedFuture()}。
 * 由于 {@code RelayError} 是密封接口（不是 {@code Throwable}），此异常充当桥接。
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
