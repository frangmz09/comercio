package dev.francogomez.comercio.core.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void loginConCredencialesValidasDevuelveUnToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyString())))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void unaContraseniaIncorrectaDevuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"la-que-no-es"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void unUsuarioInexistenteDevuelveElMismoMensajeQueUnaContraseniaMala() throws Exception {
        // No distinguir los dos casos es deliberado: hacerlo le confirmaría a un atacante
        // qué usuarios existen.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nadie","password":"cualquiera"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void consultarNoRequiereToken() throws Exception {
        mockMvc.perform(get("/api/v1/productos")).andExpect(status().isOk());
    }

    @Test
    void escribirSinTokenDevuelve401ConLaFormaDeErrorHabitual() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SIN-TOKEN","nombre":"x","categoria":"y","unidad":"unidad"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/productos"));
    }

    @Test
    void unTokenAdulteradoNoAutentica() throws Exception {
        String token = tokenDe("admin", "admin123");
        // Cambiar un solo carácter del payload invalida la firma.
        String adulterado = token.substring(0, token.length() - 3) + "xyz";

        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + adulterado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"ADULT-1","nombre":"x","categoria":"y","unidad":"unidad"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elVendedorPuedeVenderPeroNoTocarElCatalogo() throws Exception {
        String token = tokenDe("vendedor", "vendedor123");

        // El catálogo es de ADMIN: autenticado pero sin permiso, es 403 y no 401.
        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"VEND-%s","nombre":"x","categoria":"y","unidad":"unidad"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // Mover stock sí le corresponde: llega al controlador y falla por el producto
        // inexistente, no por permisos.
        mockMvc.perform(post("/api/v1/stock/movimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"%s","tipo":"ENTRADA","cantidad":1,"motivo":"prueba de rol"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAdminPuedeCrearProductos() throws Exception {
        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + tokenDe("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"ADM-%s","nombre":"Producto de admin","categoria":"Test","unidad":"unidad"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isCreated());
    }

    @Test
    void unTokenVencidoNoAutentica() {
        // La vigencia se valida en el propio servicio: emitir con vigencia negativa
        // produce un token ya expirado, sin tener que esperar una hora en el test.
        JwtService expirado = new JwtService("secreto-de-prueba-con-mas-de-32-caracteres", -1);
        String token = expirado.emitirPara(new Usuario("admin", "irrelevante", Rol.ADMIN));

        org.assertj.core.api.Assertions.assertThat(jwtService.validar(token)).isEmpty();
    }

    private String tokenDe(String username, String password) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(cuerpo).get("token").asText();
    }
}
