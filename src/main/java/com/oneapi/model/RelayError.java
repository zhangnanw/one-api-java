package com.oneapi.model;

/**
 * 中继失败的类型化错误层次。
 */
public sealed interface RelayError
    permits RelayError.ModelNotFound, RelayError.NoInstance,
            RelayError.AllVendorsBusy, RelayError.UpstreamFailure,
            RelayError.BodyTooLarge, RelayError.DirectUseForbidden {

    record ModelNotFound(String requestedModel) implements RelayError {}
    record NoInstance(String model, String reason) implements RelayError {}
    record AllVendorsBusy(int retried) implements RelayError {}
    record UpstreamFailure(int httpCode, String responseBody) implements RelayError {}
    record BodyTooLarge(String model, int bodyBytes, int windowTokens) implements RelayError {}
    /**
     * 用户直接使用了物理模型名（{@code instances.model_name}）请求。
     * 按 {@code requirements §B.2}，API 表面只接受虚拟模型名。
     * 命中时直接 404 + 提示用户去注册虚拟模型。
     */
    record DirectUseForbidden(String physicalModelName) implements RelayError {}

    /** 映射到 HTTP 状态码 */
    default int httpStatus() {
        if (this instanceof ModelNotFound) {
            return 404;
        } else if (this instanceof DirectUseForbidden) {
            return 404;
        } else if (this instanceof NoInstance) {
            return 503;
        } else if (this instanceof AllVendorsBusy) {
            return 503;
        } else if (this instanceof UpstreamFailure f) {
            return f.httpCode();
        } else if (this instanceof BodyTooLarge) {
            return 413;
        } else {
            return 500;  // 兜底
        }
    }

    /** JSON 响应的错误类型字符串 */
    default String typeName() {
        return getClass().getSimpleName();
    }
}