package dev.francogomez.comercio.core.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Swagger es la puerta de entrada a esta API: es lo que abre quien no la conoce, y si lo
 * que promete no coincide con lo que la API hace, manda a buscar el problema al lugar
 * equivocado. Este test toma el documento OpenAPI que sirve la aplicación y verifica que
 * cumpla las condiciones que lo vuelven usable sin leer el código.
 *
 * <p>Lo que se comprueba no es cosmético. Un cuerpo de ejemplo con {@code "string"} donde
 * va un UUID hace fallar toda escritura hecha desde "Try it out", y una operación que
 * solo declara 200 esconde justamente el contrato de errores —los 409 de concurrencia—
 * que es lo más trabajado del proyecto.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ContratoOpenApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private JsonNode documento;

    @Autowired
    private MockMvc mockMvc;

    /** El documento es el mismo para todos los casos; se pide una vez y se reusa. */
    private JsonNode documento() throws Exception {
        if (documento == null) {
            String json = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            documento = new ObjectMapper().readTree(json);
        }
        return documento;
    }

    @Test
    void todaOperacionDeclaraElCodigoDeExitoQueLaApiDevuelve() throws Exception {
        List<String> sinExito = new ArrayList<>();

        recorrerOperaciones(documento(), (identificador, operacion) -> {
            boolean declaraAlgun2xx = campos(operacion.get("responses"))
                    .anyMatch(codigo -> codigo.startsWith("2"));
            if (!declaraAlgun2xx) {
                sinExito.add(identificador);
            }
        });

        assertThat(sinExito).as("operaciones sin ninguna respuesta 2xx documentada").isEmpty();
    }

    /**
     * springdoc infiere un 200 para toda operación aunque el controller devuelva otra
     * cosa. Publicar las dos deja el documento diciendo que la API responde 200 y 201
     * para lo mismo, cuando solo hace una.
     */
    @Test
    void ningunaOperacionDeclaraDosCodigosDeExitoDistintos() throws Exception {
        Map<String, List<String>> conflictivas = new java.util.LinkedHashMap<>();

        recorrerOperaciones(documento(), (identificador, operacion) -> {
            List<String> exitosos = campos(operacion.get("responses"))
                    .filter(codigo -> codigo.startsWith("2"))
                    .toList();
            if (exitosos.size() > 1) {
                conflictivas.put(identificador, exitosos);
            }
        });

        assertThat(conflictivas).as("operaciones con más de un código de éxito").isEmpty();
    }

    /**
     * Que el 401 esté documentado importa incluso más que los demás: es el error que se
     * come cualquiera que prueba la API sin haber apretado Authorize.
     */
    @Test
    void lasOperacionesQueModificanEstadoDeclaran401() throws Exception {
        List<String> sin401 = new ArrayList<>();

        recorrerOperaciones(documento(), (identificador, operacion) -> {
            if (identificador.startsWith("GET ") || identificador.contains("/auth/login")) {
                return;
            }
            if (!operacion.get("responses").has("401")) {
                sin401.add(identificador);
            }
        });

        assertThat(sin401).as("operaciones de escritura sin 401 documentado").isEmpty();
    }

    /** Los GET son públicos en {@code SecurityConfig}; el documento no debe pedirles token. */
    @Test
    void lasConsultasNoAparecenComoProtegidas() throws Exception {
        List<String> conCandado = new ArrayList<>();

        recorrerOperaciones(documento(), (identificador, operacion) -> {
            if (!identificador.startsWith("GET ")) {
                return;
            }
            JsonNode seguridad = operacion.get("security");
            if (seguridad != null && !seguridad.isEmpty()) {
                conCandado.add(identificador);
            }
        });

        assertThat(conCandado).as("consultas públicas documentadas como si pidieran token").isEmpty();
    }

    /**
     * Sin ejemplos, Swagger UI arma el cuerpo con {@code "string"} en cada campo y todo
     * UUID falla al convertirse. Es la diferencia entre una demo que se prueba apretando
     * Execute y una que exige leer el código para saber qué mandar.
     *
     * <p>Quedan afuera dos casos donde el ejemplo lo aporta otra parte del documento: los
     * enums, cuyos valores admitidos springdoc ya publica, y los campos que apuntan a
     * otro schema —{@code VentaRequest.lineas} es una lista de {@code LineaRequest}, y
     * los ejemplos que Swagger usa para armar el cuerpo son los de ese schema.
     */
    @Test
    void cadaCampoDeUnRequestTraeUnEjemploUtilizable() throws Exception {
        JsonNode schemas = documento().get("components").get("schemas");
        List<String> sinEjemplo = new ArrayList<>();

        campos(schemas)
                .filter(nombre -> nombre.endsWith("Request"))
                .forEach(nombre -> {
                    JsonNode propiedades = schemas.get(nombre).get("properties");
                    campos(propiedades)
                            .filter(campo -> necesitaEjemplo(propiedades.get(campo)))
                            .forEach(campo -> sinEjemplo.add(nombre + "." + campo));
                });

        assertThat(sinEjemplo).as("campos de request sin example").isEmpty();
    }

    private static boolean necesitaEjemplo(JsonNode campo) {
        if (campo.has("example") || campo.has("enum") || campo.has("$ref")) {
            return false;
        }
        // Una lista cuyo elemento es otro schema: el ejemplo vive allá.
        return !campo.path("items").has("$ref");
    }

    @Test
    void elSchemaDeErrorEstaPublicadoYLasRespuestasDeErrorLoReferencian() throws Exception {
        assertThat(documento().get("components").get("schemas").has("ApiError"))
                .as("ApiError publicado en components")
                .isTrue();

        List<String> malReferenciadas = new ArrayList<>();
        recorrerOperaciones(documento(), (identificador, operacion) -> {
            JsonNode respuestas = operacion.get("responses");
            campos(respuestas)
                    .filter(codigo -> codigo.startsWith("4") || codigo.startsWith("5"))
                    .forEach(codigo -> {
                        JsonNode schema = respuestas.get(codigo)
                                .path("content").path("application/json").path("schema");
                        if (!"#/components/schemas/ApiError".equals(schema.path("$ref").asText())) {
                            malReferenciadas.add(identificador + " -> " + codigo);
                        }
                    });
        });

        assertThat(malReferenciadas).as("respuestas de error que no devuelven ApiError").isEmpty();
    }

    /** Todo campo obligatorio del movimiento es un dato; el getter de validación no va al schema. */
    @Test
    void elGetterDeValidacionNoSePublicaComoCampo() throws Exception {
        JsonNode movimiento = documento().get("components").get("schemas").get("MovimientoRequest");

        assertThat(campos(movimiento.get("properties")))
                .as("campos publicados de MovimientoRequest")
                .doesNotContain("cantidadDistintaDeCero");
    }

    private static void recorrerOperaciones(JsonNode documento, VisitaDeOperacion visita) {
        JsonNode rutas = documento.get("paths");
        campos(rutas).forEach(ruta ->
                campos(rutas.get(ruta)).forEach(metodo ->
                        visita.visitar(metodo.toUpperCase() + " " + ruta, rutas.get(ruta).get(metodo))));
    }

    private static Stream<String> campos(JsonNode nodo) {
        if (nodo == null || nodo.isMissingNode()) {
            return Stream.empty();
        }
        Iterator<String> nombres = nodo.fieldNames();
        List<String> lista = new ArrayList<>();
        nombres.forEachRemaining(lista::add);
        return lista.stream();
    }

    @FunctionalInterface
    private interface VisitaDeOperacion {
        void visitar(String identificador, JsonNode operacion);
    }
}
