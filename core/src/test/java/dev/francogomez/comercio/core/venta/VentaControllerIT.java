package dev.francogomez.comercio.core.venta;

import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ejercita las ventas por HTTP real, no llamando al servicio. Es lo que distingue este
 * test de {@link VentaIT}: acá la respuesta se serializa de verdad, que es donde
 * aparecen los problemas de asociaciones lazy accedidas fuera de la transacción.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
// Se inyecta la autenticación directamente en el contexto en lugar de pedir un token
// real: lo que estos casos verifican es el comportamiento de negocio, y la mecánica del
// JWT tiene sus propios tests en AutenticacionIT.
@WithMockUser(roles = "ADMIN")
class VentaControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    private String productoId;
    private String listaId;
    private String puntoVentaId;

    @BeforeEach
    void prepararCatalogo() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        productoId = idDe(mockMvc.perform(post("/api/v1/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku":"HTTP-%s","nombre":"Fideos 500g","categoria":"Almacén","unidad":"unidad"}
                        """.formatted(sufijo))));

        listaId = idDe(mockMvc.perform(post("/api/v1/listas-precio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"L-%s","nombre":"Minorista"}
                        """.formatted(sufijo))));

        puntoVentaId = idDe(mockMvc.perform(post("/api/v1/puntos-venta")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"numero":%d,"nombre":"Caja de prueba"}
                        """.formatted(ThreadLocalRandom.current().nextInt(1, 1_000_000)))));

        mockMvc.perform(post("/api/v1/listas-precio/" + listaId + "/precios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"%s","monto":1250.00}
                                """.formatted(productoId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/stock/movimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"%s","tipo":"ENTRADA","cantidad":100,"motivo":"carga inicial"}
                                """.formatted(productoId)))
                .andExpect(status().isCreated());
    }

    @Test
    void registrarUnaVentaDevuelveElComprobanteEmitidoConSuNumeroFormateado() throws Exception {
        mockMvc.perform(post("/api/v1/ventas")
                        .header("Idempotency-Key", "http-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoDe(3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(3750.00))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.lineas", hasSize(1)))
                .andExpect(jsonPath("$.lineas[0].precioUnitario").value(1250.00))
                // El número formateado obliga a resolver el punto de venta al serializar:
                // si la asociación quedara lazy, acá explota con 500.
                .andExpect(jsonPath("$.comprobantes", hasSize(1)))
                .andExpect(jsonPath("$.comprobantes[0].tipo").value("FACTURA"))
                .andExpect(jsonPath("$.comprobantes[0].numero").value(matchesPattern("\\d{4,}-\\d{8}")));
    }

    @Test
    void consultarLaVentaDevuelveLoMismoQueElAlta() throws Exception {
        String ventaId = idDe(mockMvc.perform(post("/api/v1/ventas")
                .header("Idempotency-Key", "http-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoDe(2))));

        mockMvc.perform(get("/api/v1/ventas/" + ventaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2500.00))
                .andExpect(jsonPath("$.comprobantes[0].tipo").value("FACTURA"));
    }

    @Test
    void laNotaDeCreditoDevuelveElStockYDejaLaVentaAnulada() throws Exception {
        String ventaId = idDe(mockMvc.perform(post("/api/v1/ventas")
                .header("Idempotency-Key", "http-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoDe(4))));

        mockMvc.perform(get("/api/v1/stock/" + productoId))
                .andExpect(jsonPath("$.cantidad").value(96));

        mockMvc.perform(post("/api/v1/ventas/" + ventaId + "/nota-credito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"puntoVentaId":"%s","motivo":"devolución del cliente"}
                                """.formatted(puntoVentaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("NOTA_CREDITO"))
                .andExpect(jsonPath("$.revierteA").isNotEmpty());

        mockMvc.perform(get("/api/v1/stock/" + productoId))
                .andExpect(jsonPath("$.cantidad").value(100));

        mockMvc.perform(get("/api/v1/ventas/" + ventaId))
                .andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.comprobantes", hasSize(2)));
    }

    @Test
    void sinHeaderDeIdempotenciaDevuelve400YNo500() throws Exception {
        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoDe(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el header obligatorio 'Idempotency-Key'"));
    }

    @Test
    void venderMasDeLoQueHayEnStockDevuelve409() throws Exception {
        mockMvc.perform(post("/api/v1/ventas")
                        .header("Idempotency-Key", "http-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoDe(9999)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private String pedidoDe(int cantidad) {
        return """
                {"listaPrecioId":"%s","puntoVentaId":"%s","lineas":[{"productoId":"%s","cantidad":%d}]}
                """.formatted(listaId, puntoVentaId, productoId, cantidad);
    }

    private String idDe(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(cuerpo).get("id").asText();
    }
}
