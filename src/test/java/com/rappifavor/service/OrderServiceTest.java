package com.rappifavor.service;

import com.rappifavor.model.EstadoPedido;
import com.rappifavor.model.Pedido;
import com.rappifavor.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrderServiceTest — Tests unitarios para OrderService.
 *
 * Cubre todas las transiciones del AFD definido en el SRS Sección 6.1:
 *
 *   crear         → PENDIENTE
 *   aceptar       → PENDIENTE  → ASIGNADO
 *   iniciarEntrega→ ASIGNADO   → EN_CAMINO
 *   confirmar     → EN_CAMINO  → ENTREGADO
 *   cancelar      → PENDIENTE  → CANCELADO
 *   cancelar      → ASIGNADO   → CANCELADO
 *   marcarDisputado → ENTREGADO → DISPUTADO
 *   marcarResuelto  → DISPUTADO → RESUELTO
 *
 * No requiere conexión a MongoDB.
 * Para correr solo estos tests: mvn test -Dtest=OrderServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — Tests del AFD")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Pedido pedidoEn(EstadoPedido estado) {
        Pedido p = new Pedido("Traer café", "Cafetería", "Lab L101", "uid-usuario");
        p.setId("pedido-id-001");
        p.setEstado(estado);
        if (estado != EstadoPedido.PENDIENTE) {
            p.setRepartidorId("uid-repartidor");
        }
        return p;
    }

    // ─── crear() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear: campos válidos → pedido en PENDIENTE con historial")
    void crear_camposValidos_retornaPedidoPendiente() {
        doNothing().when(orderRepository).save(any());

        Pedido p = orderService.crear("Traer café", "Cafetería", "Lab L101", "uid-usuario");

        assertEquals(EstadoPedido.PENDIENTE, p.getEstado());
        assertEquals("uid-usuario", p.getUsuarioId());
        assertNotNull(p.getCreadoEn());
        assertNotNull(p.getActualizadoEn());
        assertFalse(p.getHistorial().isEmpty());
        assertEquals(EstadoPedido.PENDIENTE, p.getHistorial().get(0).getEstado());
        verify(orderRepository).save(p);
    }

    @Test
    @DisplayName("crear: descripción vacía → IllegalArgumentException")
    void crear_descripcionVacia_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
            orderService.crear("", "Origen", "Destino", "uid-usuario"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: origen vacío → IllegalArgumentException")
    void crear_origenVacio_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
            orderService.crear("Descripción", "", "Destino", "uid-usuario"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: destino vacío → IllegalArgumentException")
    void crear_destinoVacio_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
            orderService.crear("Descripción", "Origen", "", "uid-usuario"));
        verify(orderRepository, never()).save(any());
    }

    // ─── aceptar() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("aceptar: PENDIENTE → ASIGNADO con repartidorId asignado")
    void aceptar_pedidoPendiente_transicionaAAsignado() {
        Pedido p = pedidoEn(EstadoPedido.PENDIENTE);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));
        when(orderRepository.update(any())).thenReturn(true);

        Pedido resultado = orderService.aceptar("pedido-id-001", "uid-repartidor");

        assertEquals(EstadoPedido.ASIGNADO, resultado.getEstado());
        assertEquals("uid-repartidor", resultado.getRepartidorId());
        assertTrue(resultado.getHistorial().stream()
            .anyMatch(h -> h.getEstado() == EstadoPedido.ASIGNADO));
    }

    @Test
    @DisplayName("aceptar: pedido ya ASIGNADO → IllegalStateException")
    void aceptar_pedidoAsignado_lanzaExcepcion() {
        Pedido p = pedidoEn(EstadoPedido.ASIGNADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class, () ->
            orderService.aceptar("pedido-id-001", "uid-repartidor"));
        verify(orderRepository, never()).update(any());
    }

    @Test
    @DisplayName("aceptar: pedido inexistente → IllegalArgumentException")
    void aceptar_pedidoInexistente_lanzaExcepcion() {
        when(orderRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            orderService.aceptar("no-existe", "uid-repartidor"));
    }

    // ─── iniciarEntrega() ────────────────────────────────────────────────────

    @Test
    @DisplayName("iniciarEntrega: ASIGNADO → EN_CAMINO")
    void iniciarEntrega_pedidoAsignado_transicionaAEnCamino() {
        Pedido p = pedidoEn(EstadoPedido.ASIGNADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));
        when(orderRepository.update(any())).thenReturn(true);

        Pedido resultado = orderService.iniciarEntrega("pedido-id-001", "uid-repartidor");

        assertEquals(EstadoPedido.EN_CAMINO, resultado.getEstado());
        assertTrue(resultado.getHistorial().stream()
            .anyMatch(h -> h.getEstado() == EstadoPedido.EN_CAMINO));
    }

    @Test
    @DisplayName("iniciarEntrega: repartidor incorrecto → IllegalArgumentException")
    void iniciarEntrega_repartidorIncorrecto_lanzaExcepcion() {
        Pedido p = pedidoEn(EstadoPedido.ASIGNADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalArgumentException.class, () ->
            orderService.iniciarEntrega("pedido-id-001", "uid-otro-repartidor"));
        verify(orderRepository, never()).update(any());
    }

    @Test
    @DisplayName("iniciarEntrega: pedido PENDIENTE → IllegalStateException")
    void iniciarEntrega_pedidoPendiente_lanzaExcepcion() {
        Pedido p = pedidoEn(EstadoPedido.PENDIENTE);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class, () ->
            orderService.iniciarEntrega("pedido-id-001", "uid-repartidor"));
    }

    // ─── confirmarEntrega() ──────────────────────────────────────────────────

    @Test
    @DisplayName("confirmarEntrega: EN_CAMINO → ENTREGADO")
    void confirmarEntrega_enCamino_transicionaAEntregado() {
        Pedido p = pedidoEn(EstadoPedido.EN_CAMINO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));
        when(orderRepository.update(any())).thenReturn(true);

        Pedido resultado = orderService.confirmarEntrega("pedido-id-001", "uid-repartidor");

        assertEquals(EstadoPedido.ENTREGADO, resultado.getEstado());
        assertTrue(resultado.getHistorial().stream()
            .anyMatch(h -> h.getEstado() == EstadoPedido.ENTREGADO));
    }

    @Test
    @DisplayName("confirmarEntrega: pedido ASIGNADO (no EN_CAMINO) → IllegalStateException")
    void confirmarEntrega_pedidoAsignado_lanzaExcepcion() {
        Pedido p = pedidoEn(EstadoPedido.ASIGNADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class, () ->
            orderService.confirmarEntrega("pedido-id-001", "uid-repartidor"));
    }

    // ─── cancelar() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelar: PENDIENTE → CANCELADO por el usuario dueño")
    void cancelar_pedidoPendiente_transicionaACancelado() {
        Pedido p = pedidoEn(EstadoPedido.PENDIENTE);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));
        when(orderRepository.update(any())).thenReturn(true);

        Pedido resultado = orderService.cancelar("pedido-id-001", "uid-usuario");

        assertEquals(EstadoPedido.CANCELADO, resultado.getEstado());
    }

    @Test
    @DisplayName("cancelar: ASIGNADO → CANCELADO por el usuario dueño")
    void cancelar_pedidoAsignado_transicionaACancelado() {
        Pedido p = pedidoEn(EstadoPedido.ASIGNADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));
        when(orderRepository.update(any())).thenReturn(true);

        Pedido resultado = orderService.cancelar("pedido-id-001", "uid-usuario");

        assertEquals(EstadoPedido.CANCELADO, resultado.getEstado());
    }

    @Test
    @DisplayName("cancelar: ENTREGADO → IllegalStateException (no se puede cancelar)")
    void cancelar_pedidoEntregado_lanzaExcepcion() {
        Pedido p = pedidoEn(EstadoPedido.ENTREGADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class, () ->
            orderService.cancelar("pedido-id-001", "uid-usuario"));
        verify(orderRepository, never()).update(any());
    }

    @Test
    @DisplayName("cancelar: usuario que no es el dueño → IllegalArgumentException")
    void cancelar_usuarioNoDueno_lanzaExcepcion() {
        Pedido p = pedidoEn(EstadoPedido.PENDIENTE);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalArgumentException.class, () ->
            orderService.cancelar("pedido-id-001", "uid-otro-usuario"));
        verify(orderRepository, never()).update(any());
    }

    // ─── marcarDisputado() ───────────────────────────────────────────────────

    @Test
    @DisplayName("marcarDisputado: ENTREGADO → DISPUTADO")
    void marcarDisputado_entregado_transicionaADisputado() {
        Pedido p = pedidoEn(EstadoPedido.ENTREGADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));
        when(orderRepository.update(any())).thenReturn(true);

        Pedido resultado = orderService.marcarDisputado("pedido-id-001");

        assertEquals(EstadoPedido.DISPUTADO, resultado.getEstado());
    }

    @Test
    @DisplayName("marcarDisputado: pedido no ENTREGADO → IllegalStateException")
    void marcarDisputado_pedidoNoEntregado_lanzaExcepcion() {
        Pedido p = pedidoEn(EstadoPedido.EN_CAMINO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class, () ->
            orderService.marcarDisputado("pedido-id-001"));
    }

    // ─── marcarResuelto() ────────────────────────────────────────────────────

    @Test
    @DisplayName("marcarResuelto: DISPUTADO → RESUELTO")
    void marcarResuelto_disputado_transicionaAResuelto() {
        Pedido p = pedidoEn(EstadoPedido.DISPUTADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));
        when(orderRepository.update(any())).thenReturn(true);

        Pedido resultado = orderService.marcarResuelto("pedido-id-001");

        assertEquals(EstadoPedido.RESUELTO, resultado.getEstado());
    }

    @Test
    @DisplayName("marcarResuelto: pedido no DISPUTADO → IllegalStateException")
    void marcarResuelto_pedidoNoDisputado_lanzaExcepcion() {
        Pedido p = pedidoEn(EstadoPedido.ENTREGADO);
        when(orderRepository.findById("pedido-id-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class, () ->
            orderService.marcarResuelto("pedido-id-001"));
    }

    // ─── Consultas ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll retorna lista de pedidos")
    void findAll_retornaListaDePedidos() {
        when(orderRepository.findAll()).thenReturn(List.of(
            pedidoEn(EstadoPedido.PENDIENTE),
            pedidoEn(EstadoPedido.ASIGNADO)
        ));

        assertEquals(2, orderService.findAll().size());
    }

    @Test
    @DisplayName("findByEstado filtra por estado del AFD")
    void findByEstado_filtraCorrectamente() {
        Pedido p = pedidoEn(EstadoPedido.PENDIENTE);
        when(orderRepository.findByEstado(EstadoPedido.PENDIENTE)).thenReturn(List.of(p));

        List<Pedido> resultado = orderService.findByEstado(EstadoPedido.PENDIENTE);

        assertEquals(1, resultado.size());
        assertEquals(EstadoPedido.PENDIENTE, resultado.get(0).getEstado());
    }
}
