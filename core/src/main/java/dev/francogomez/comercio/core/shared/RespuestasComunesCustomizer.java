package dev.francogomez.comercio.core.shared;

import dev.francogomez.comercio.core.auth.AuthController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;

/**
 * Completa cada operación con las respuestas que no dependen del endpoint sino de cómo
 * está configurada la seguridad y el manejo de errores: 400 donde hay cuerpo que
 * validar, 401 y 403 donde hace falta token, y 500 en todas.
 *
 * <p>Va acá y no como anotación repetida en los 23 endpoints por una razón práctica: son
 * reglas transversales, y escritas veintitrés veces se desincronizan la primera vez que
 * una cambia. Lo específico de cada operación —201, 404, 409— sí se anota en el
 * controller, donde se lee junto al código que lo produce.
 *
 * <p>Que esto refleje la configuración real de seguridad no queda librado a la memoria:
 * {@code ContratoOpenApiIT} compara lo documentado contra lo que la API responde.
 */
@Component
public class RespuestasComunesCustomizer implements OperationCustomizer {

    /** Registrado en {@link OpenApiConfig}; acá solo se lo referencia. */
    private static final String REF_API_ERROR = "#/components/schemas/ApiError";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiResponses respuestas = operation.getResponses();

        if (esPublica(handlerMethod)) {
            // Anula el requisito de seguridad global para esta operación. Sin esto Swagger
            // muestra el candado en los GET, que son justamente los que no piden token.
            operation.setSecurity(List.of());
        } else {
            agregar(respuestas, "401", "Falta el token, venció o no es válido");
            agregar(respuestas, "403", "El rol del token no alcanza para esta operación");
        }

        if (recibeCuerpo(handlerMethod)) {
            agregar(respuestas, "400", "El cuerpo tiene campos inválidos o mal tipados");
        }
        agregar(respuestas, "500", "Error interno inesperado");

        corregirElCodigoDeExito(respuestas);
        completarLosErroresConApiError(respuestas);
        return operation;
    }

    /**
     * Refleja lo que decide {@code SecurityConfig}: consultar es abierto, el login
     * también, y todo lo que modifica estado pide token.
     */
    private static boolean esPublica(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(GetMapping.class)
                || AuthController.class.equals(handlerMethod.getBeanType());
    }

    private static boolean recibeCuerpo(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parametro -> parametro.hasParameterAnnotation(RequestBody.class));
    }

    /**
     * springdoc asume un 200 para toda operación —no deduce el 201 de un
     * {@code ResponseEntity.created()}— y lo deja aunque el controller documente otro
     * código. Lo que infirió bien es el cuerpo, así que se lo pasa al código correcto y
     * descarta el 200, en vez de publicar dos respuestas donde la API devuelve una.
     */
    private static void corregirElCodigoDeExito(ApiResponses respuestas) {
        ApiResponse presunta = respuestas.get("200");
        if (presunta == null) {
            return;
        }
        respuestas.entrySet().stream()
                .filter(entrada -> entrada.getKey().startsWith("2") && !"200".equals(entrada.getKey()))
                .findFirst()
                .ifPresent(declarada -> {
                    if (declarada.getValue().getContent() == null) {
                        declarada.getValue().setContent(presunta.getContent());
                    }
                    respuestas.remove("200");
                });
    }

    /**
     * Todo error de esta API sale como {@link ApiError}, sin excepción. Centralizarlo
     * permite que los controllers declaren sus 404 y 409 con una línea, sin repetir el
     * {@code @Content} veinte veces.
     *
     * <p>Pisa lo que haya en lugar de completar solo lo vacío: a un {@code @ApiResponse}
     * sin content declarado springdoc le adjudica el tipo que devuelve el método —el 404
     * de buscar un producto termina documentado como si devolviera un ProductoResponse—,
     * y eso es peor que no documentar nada.
     */
    private static void completarLosErroresConApiError(ApiResponses respuestas) {
        respuestas.forEach((codigo, respuesta) -> {
            if (esError(codigo)) {
                respuesta.setContent(contenidoDeError());
            }
        });
    }

    private static boolean esError(String codigo) {
        return codigo.startsWith("4") || codigo.startsWith("5");
    }

    /** No pisa lo que el controller haya documentado por su cuenta para ese código. */
    private static void agregar(ApiResponses respuestas, String codigo, String descripcion) {
        respuestas.computeIfAbsent(codigo, ignorado -> new ApiResponse()
                .description(descripcion)
                .content(contenidoDeError()));
    }

    private static Content contenidoDeError() {
        return new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref(REF_API_ERROR)));
    }
}
