package dev.francogomez.comercio.core.shared;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La promesa de la precarga es que el cuerpo de ejemplo de {@code POST /ventas} funcione
 * tal cual lo propone Swagger. Si alguien cambia un id de los ejemplos sin tocar el seed,
 * o al revés, esto lo detecta.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "comercio.demo.seed-datos=true")
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class SeedDatosDemoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Test
    void laVentaDeEjemploDeSwaggerSaleContraLosDatosPrecargados() throws Exception {
        String ejemplo = """
                {"listaPrecioId":"1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
                 "puntoVentaId":"2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a",
                 "lineas":[{"productoId":"9b1f2c3d-4e5a-4b6c-8d7e-0f1a2b3c4d5e","cantidad":2}]}
                """;

        mockMvc.perform(post("/api/v1/ventas")
                        .header("Idempotency-Key", "caja-3-20260813-000417")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ejemplo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(4900.00))
                .andExpect(jsonPath("$.comprobantes[0].numero").value("0003-00000001"));

        mockMvc.perform(get("/api/v1/stock/" + SeedDatosDemo.PRODUCTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(98));
    }
}
