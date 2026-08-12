package dev.francogomez.comercio.core.producto;

import dev.francogomez.comercio.core.shared.ConflictException;
import dev.francogomez.comercio.core.shared.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository repository;

    private ProductoService service;

    @BeforeEach
    void setUp() {
        service = new ProductoService(repository);
    }

    @Test
    void crear_guardaElProductoCuandoElSkuNoExiste() {
        var request = new ProductoRequest("SKU-1", "Fideos 500g", "Almacén", "unidad");
        when(repository.existsBySku("SKU-1")).thenReturn(false);
        when(repository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        Producto creado = service.crear(request);

        assertThat(creado.getSku()).isEqualTo("SKU-1");
        assertThat(creado.isActivo()).isTrue();

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Fideos 500g");
    }

    @Test
    void crear_rechazaSkuDuplicado() {
        var request = new ProductoRequest("SKU-1", "Fideos 500g", "Almacén", "unidad");
        when(repository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SKU-1");

        verify(repository, never()).save(any());
    }

    @Test
    void buscar_lanzaNotFoundCuandoNoExiste() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void actualizar_rechazaSiElNuevoSkuYaLoUsaOtroProducto() {
        UUID id = UUID.randomUUID();
        Producto existente = new Producto("SKU-1", "Fideos", "Almacén", "unidad");
        Producto otro = new Producto("SKU-2", "Arroz", "Almacén", "unidad");

        when(repository.findById(id)).thenReturn(Optional.of(existente));
        when(repository.findBySku("SKU-2")).thenReturn(Optional.of(otro));

        var request = new ProductoRequest("SKU-2", "Fideos", "Almacén", "unidad");

        assertThatThrownBy(() -> service.actualizar(id, request))
                .isInstanceOf(ConflictException.class);
    }
}
