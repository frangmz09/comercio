package dev.francogomez.comercio.core.producto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Levanta un Postgres real con Testcontainers y ejercita la API completa vía MockMvc:
 * HTTP -> controller -> service -> JPA -> Flyway -> base. Corre en la fase
 * integration-test (failsafe), separado de los tests unitarios rápidos.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
// El catálogo lo administra ADMIN; la autorización tiene sus propios tests en
// AutenticacionIT y acá solo estorbaría al caso que se quiere probar.
@WithMockUser(roles = "ADMIN")
class ProductoControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Test
    void crearBuscarYListarUnProducto() throws Exception {
        String body = """
                {"sku":"ALM-001","nombre":"Fideos 500g","categoria":"Almacén","unidad":"unidad"}
                """;

        String location = mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ALM-001"))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Fideos 500g"));

        mockMvc.perform(get("/api/v1/productos").param("categoria", "Almacén"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void crearRechazaSkuDuplicadoCon409() throws Exception {
        String body = """
                {"sku":"ALM-002","nombre":"Arroz 1kg","categoria":"Almacén","unidad":"unidad"}
                """;

        mockMvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void crearRechazaCuerpoInvalidoCon400YErroresDeCampo() throws Exception {
        String body = """
                {"sku":"","nombre":"","unidad":""}
                """;

        mockMvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.sku").exists())
                .andExpect(jsonPath("$.validationErrors.nombre").exists());
    }

    @Test
    void buscarUnIdInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/productos/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void desactivarSacaElProductoDelListadoActivo() throws Exception {
        String body = """
                {"sku":"ALM-003","nombre":"Yerba 1kg","categoria":"Almacén","unidad":"unidad"}
                """;

        String location = mockMvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(post(location + "/desactivar")).andExpect(status().isNoContent());

        mockMvc.perform(get(location)).andExpect(jsonPath("$.activo").value(false));
    }
}
