package dev.francogomez.comercio.core.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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

    @Bean
    public OpenAPI comercioOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("comercio — core transaccional")
                        .version(version)
                        .description("""
                                API REST del core transaccional de un comercio minorista: catálogo de \
                                productos, listas de precios con vigencia, stock por movimientos, ventas \
                                atómicas y comprobantes con numeración sin huecos.

                                El dominio es sintético: es un proyecto de portfolio, sin relación con \
                                ningún sistema de un empleador.

                                **Estado:** catálogo de productos operativo. Precios, stock, ventas y \
                                comprobantes se suman en las próximas iteraciones.""")
                        .contact(new Contact()
                                .name("Franco Gómez")
                                .url("https://github.com/frangmz09/comercio"))
                        .license(new License()
                                .name("MIT")
                                .url("https://github.com/frangmz09/comercio/blob/main/LICENSE")))
                .servers(List.of(
                        new Server().url("/").description("Servidor actual")));
    }
}
