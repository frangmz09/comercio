package dev.francogomez.comercio.core.shared;

import java.time.Instant;
import java.util.Map;

/** Forma única de respuesta de error para toda la API — el cliente nunca parsea un stacktrace. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError validation(int status, String message, String path, Map<String, String> validationErrors) {
        return new ApiError(Instant.now(), status, "Validation Failed", message, path, validationErrors);
    }
}
