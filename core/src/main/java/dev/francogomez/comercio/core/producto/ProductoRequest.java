package dev.francogomez.comercio.core.producto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductoRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,40}$", message = "el SKU solo admite letras, números, punto, guion y guion bajo")
        @Schema(description = "Código interno del producto, único en el catálogo", example = "ALM-FID-500")
        String sku,

        @NotBlank
        @Size(max = 200)
        @Schema(example = "Fideos tallarín 500g")
        String nombre,

        @Size(max = 100)
        @Schema(example = "Almacén")
        String categoria,

        @NotBlank
        @Size(max = 20)
        @Schema(description = "Unidad en la que se mide el stock", example = "unidad")
        String unidad
) {
}
