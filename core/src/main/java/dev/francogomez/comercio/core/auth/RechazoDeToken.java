package dev.francogomez.comercio.core.auth;

/**
 * Por qué no se pudo autenticar una petición. Existe para que el 401 diga cuál de los
 * tres casos ocurrió: los tres se arreglan de forma distinta y un mensaje único obliga
 * a quien consume la API a adivinar.
 */
public enum RechazoDeToken {

    AUSENTE("Se requiere autenticación: enviá el token en el header Authorization"),

    VENCIDO("El token venció. Pedí uno nuevo con POST /api/v1/auth/login"),

    // El error más común desde Swagger UI: pegar "Bearer eyJ..." en el diálogo Authorize,
    // que ya agrega el prefijo por su cuenta y termina mandando "Bearer Bearer eyJ...".
    INVALIDO("El token no es válido. Revisá que sea el valor completo del campo 'token' "
            + "que devuelve el login, sin el prefijo 'Bearer'");

    private final String mensaje;

    RechazoDeToken(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getMensaje() {
        return mensaje;
    }
}
