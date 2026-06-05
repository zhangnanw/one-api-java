package com.oneapi.model;

import java.time.Duration;

/**
 * Typed error hierarchy for all relay failures.
 * Replaces 503 + string-based error codes.
 */
public sealed interface RelayError
    permits RelayError.ParseError, RelayError.ModelNotFound,
            RelayError.MatchRuleError, RelayError.NoInstance,
            RelayError.NoReasoningInstance, RelayError.AllVendorsBusy,
            RelayError.UpstreamFailure, RelayError.Timeout {
    
    record ParseError(String detail) implements RelayError {}
    record ModelNotFound(String requestedModel) implements RelayError {}
    record MatchRuleError(String model, String detail) implements RelayError {}
    record NoInstance(String model, String reason) implements RelayError {}
    record NoReasoningInstance(String model) implements RelayError {}
    record AllVendorsBusy(int retried) implements RelayError {}
    record UpstreamFailure(int httpCode, String responseBody) implements RelayError {}
    record Timeout(Duration duration) implements RelayError {}
    
    /** Map to HTTP status code */
    default int httpStatus() {
        return switch (this) {
            case ParseError __ -> 400;
            case ModelNotFound __ -> 404;
            case MatchRuleError __ -> 400;
            case NoInstance __ -> 503;
            case NoReasoningInstance __ -> 503;
            case AllVendorsBusy __ -> 503;
            case UpstreamFailure f -> f.httpCode();
            case Timeout __ -> 504;
        };
    }
    
    /** Error type string for JSON response */
    default String typeName() {
        return getClass().getSimpleName();
    }
}
