package dev.francogomez.comercio.core.auth;

/** Credenciales que no corresponden a ningún usuario activo. Se traduce a 401. */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String message) {
        super(message);
    }
}
