package dev.francogomez.comercio.core.shared;

/**
 * Lanzada cuando la operación pedida choca con una regla de negocio o el estado actual
 * del recurso (SKU duplicado, stock insuficiente, etc). Se traduce a HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
