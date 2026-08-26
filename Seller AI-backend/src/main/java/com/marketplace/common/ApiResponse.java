package com.marketplace.common;

import java.time.Instant;

/**
 * Uniform envelope for every response. The agent's tool layer can rely on
 * {@code ok} without parsing HTTP status codes, which keeps tool-result
 * handling in the prompt simple and deterministic.
 */
public record ApiResponse<T>(boolean ok, T data, ApiError error, Instant at) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> fail(String code, String message, Object details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), Instant.now());
    }

    public record ApiError(String code, String message, Object details) {}
}
