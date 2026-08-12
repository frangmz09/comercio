package dev.francogomez.comercio.core.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductoRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,40}$", message = "el SKU solo admite letras, números, punto, guion y guion bajo")
        String sku,

        @NotBlank
        @Size(max = 200)
        String nombre,

        @Size(max = 100)
        String categoria,

        @NotBlank
        @Size(max = 20)
        String unidad
) {
}
