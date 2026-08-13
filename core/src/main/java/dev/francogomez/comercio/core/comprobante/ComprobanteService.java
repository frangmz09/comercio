package dev.francogomez.comercio.core.comprobante;

import dev.francogomez.comercio.core.shared.ConflictException;
import dev.francogomez.comercio.core.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ComprobanteService {

    private final ComprobanteRepository comprobanteRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final SecuenciaComprobanteDao secuencias;

    public ComprobanteService(ComprobanteRepository comprobanteRepository,
                              PuntoVentaRepository puntoVentaRepository,
                              SecuenciaComprobanteDao secuencias) {
        this.comprobanteRepository = comprobanteRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.secuencias = secuencias;
    }

    @Transactional
    public PuntoVenta crearPuntoVenta(int numero, String nombre) {
        if (puntoVentaRepository.existsByNumero(numero)) {
            throw new ConflictException("Ya existe un punto de venta con número " + numero);
        }
        return puntoVentaRepository.save(new PuntoVenta(numero, nombre));
    }

    /**
     * Emite un comprobante tomando el siguiente número de la secuencia. Si la transacción
     * que lo llama se revierte, el número reservado vuelve atrás con ella: por eso la
     * secuencia es una tabla y no una SEQUENCE de Postgres, que no es transaccional.
     */
    @Transactional
    public Comprobante emitir(UUID puntoVentaId, TipoComprobante tipo, UUID ventaId,
                              BigDecimal total, UUID revierteA) {
        PuntoVenta puntoVenta = puntoVentaRepository.findById(puntoVentaId)
                .orElseThrow(() -> new NotFoundException("No existe un punto de venta con id " + puntoVentaId));

        if (comprobanteRepository.existsByVentaIdAndTipo(ventaId, tipo)) {
            throw new ConflictException(
                    "La venta %s ya tiene un comprobante de tipo %s".formatted(ventaId, tipo));
        }

        long numero = secuencias.reservarNumero(puntoVentaId, tipo);
        return comprobanteRepository.save(
                new Comprobante(puntoVenta, tipo, numero, ventaId, total, revierteA));
    }

    public Comprobante buscar(UUID id) {
        return comprobanteRepository.findWithPuntoVentaById(id)
                .orElseThrow(() -> new NotFoundException("No existe un comprobante con id " + id));
    }

    public List<Comprobante> deVenta(UUID ventaId) {
        return comprobanteRepository.findByVentaIdOrderByCreadoEnAsc(ventaId);
    }

    public Comprobante facturaDe(UUID ventaId) {
        return comprobanteRepository.findByVentaIdAndTipo(ventaId, TipoComprobante.FACTURA)
                .orElseThrow(() -> new NotFoundException("La venta " + ventaId + " no tiene factura emitida"));
    }
}
