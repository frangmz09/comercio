package dev.francogomez.comercio.core.shared;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger UI es la cara visible de la API, así que la portada lleva nombre y contexto
 * propios en lugar del "OpenAPI definition" por defecto de springdoc.
 */
@Configuration
public class OpenApiConfig {

    private final String version;

    public OpenApiConfig(@Value("${app.version:0.0.0}") String version) {
        this.version = version;
    }

    /** Nombre del esquema de seguridad; se referencia desde el requisito global. */
    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI comercioOpenAPI() {
        return new OpenAPI()
                // Habilita el botón Authorize de Swagger UI: se pega el token una vez y
                // queda aplicado a todas las llamadas, sin copiarlo request por request.
                .components(componentes().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        Token devuelto por POST /api/v1/auth/login.

                                        Usuarios de la demo: admin/admin123 (ADMIN) y
                                        vendedor/vendedor123 (VENDEDOR). Las consultas GET no
                                        requieren token.""")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .info(new Info()
                        .title("comercio — core transaccional")
                        .version(version)
                        .description("""
                                API REST del core transaccional de un comercio minorista: catálogo de \
                                productos, listas de precios con vigencia, stock por movimientos, ventas \
                                atómicas y comprobantes con numeración sin huecos.

                                El dominio es sintético: es un proyecto de portfolio, sin relación con \
                                ningún sistema de un empleador.

                                Las consultas son públicas; las operaciones que modifican estado \
                                requieren token. El servicio de reportes, que proyecta estos datos \
                                desde eventos, corre aparte y no forma parte de esta demo.

                                La demo trae precargados un producto con precio y 100 unidades de \
                                stock, una lista de precios y un punto de venta, con los mismos ids \
                                que proponen los ejemplos: registrar una venta sale con Execute, sin \
                                completar nada.""")
                        .contact(new Contact()
                                .name("Franco Gómez")
                                .url("https://github.com/frangmz09/comercio"))
                        .license(new License()
                                .name("MIT")
                                .url("https://github.com/frangmz09/comercio/blob/main/LICENSE")))
                .servers(List.of(
                        new Server().url("/").description("Servidor actual")));
    }

    /**
     * Registra {@link ApiError} en el documento. Ningún controller lo devuelve —sale del
     * {@code @RestControllerAdvice}, que springdoc no escanea—, así que sin esto las
     * respuestas de error quedarían apuntando a un schema inexistente.
     */
    private static Components componentes() {
        Components components = new Components();
        ModelConverters.getInstance()
                .readAllAsResolvedSchema(ApiError.class)
                .referencedSchemas
                .forEach(components::addSchemas);
        return components;
    }
}
