package dev.francogomez.comercio.core.shared;

/** Lanzada cuando se busca una entidad por id/clave y no existe. Se traduce a HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
