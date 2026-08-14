package dev.francogomez.comercio.reportes.api;

import dev.francogomez.comercio.reportes.proyeccion.ProductoVendido;
import dev.francogomez.comercio.reportes.proyeccion.ProyeccionService;
import dev.francogomez.comercio.reportes.proyeccion.VentaDiaria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = "Reportes", description = "Agregados proyectados desde los eventos de ventas")
public class ReporteController {

    private final ProyeccionService service;

    public ReporteController(ProyeccionService service) {
        this.service = service;
    }

    @GetMapping("/ventas-diarias")
    @Operation(summary = "Ventas agregadas por día",
            description = """
                    Los totales se proyectan desde los eventos que publica `core`. Son
                    eventualmente consistentes: una venta recién registrada puede tardar un
                    instante en aparecer acá.""")
    public List<VentaDiariaResponse> ventasDiarias(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        // En UTC y no en la zona del servidor, porque las proyecciones se agrupan por
        // fecha UTC. Con LocalDate.now() a secas, un servidor en Argentina (UTC-3) entre
        // las 21 y la medianoche calcula el día anterior y las ventas de hoy quedan
        // fuera del rango por defecto.
        LocalDate fin = hasta != null ? hasta : LocalDate.now(ZoneOffset.UTC);
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);

        return service.ventasEntre(inicio, fin).stream().map(VentaDiariaResponse::from).toList();
    }

    @GetMapping("/mas-vendidos")
    @Operation(summary = "Los diez productos con más unidades vendidas")
    public List<ProductoVendidoResponse> masVendidos() {
        return service.masVendidos().stream().map(ProductoVendidoResponse::from).toList();
    }

    public record VentaDiariaResponse(
            LocalDate fecha,
            long cantidadVentas,
            BigDecimal totalVendido,
            BigDecimal totalAnulado,
            BigDecimal neto) {

        static VentaDiariaResponse from(VentaDiaria v) {
            return new VentaDiariaResponse(
                    v.getFecha(), v.getCantidadVentas(), v.getTotalVendido(),
                    v.getTotalAnulado(), v.getNeto());
        }
    }

    public record ProductoVendidoResponse(
            String sku,
            BigDecimal unidades,
            BigDecimal totalFacturado,
            Instant ultimaVenta) {

        static ProductoVendidoResponse from(ProductoVendido p) {
            return new ProductoVendidoResponse(
                    p.getSku(), p.getUnidades(), p.getTotalFacturado(), p.getUltimaVenta());
        }
    }
}
