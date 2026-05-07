package com.rappifavor.service;

import com.rappifavor.model.Disputa;
import com.rappifavor.model.EstadoPedido;
import com.rappifavor.model.Pedido;
import com.rappifavor.repository.DisputeRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DisputeServiceTest — Tests unitarios para DisputeService.
 *
 * Cubre el ciclo de vida de una disputa (FR-09, FR-10):
 *  - Abrir disputa sobre pedido ENTREGADO
 *  - Rechazar apertura en estados incorrectos
 *  - Rechazar si el usuario no es el dueño del pedido
 *  - Rechazar si ya existe una disputa para ese pedido
 *  - Resolver disputa abierta
 *  - Rechazar resolución de disputa ya resuelta
 *
 * No requiere conexión a MongoDB ni Firebase.
 * Para correr solo estos tests: mvn test -Dtest=DisputeServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeService — Tests unitarios")
class DisputeServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private OrderService orderService;

    private DisputeService disputeService;

    @BeforeEach
    void setUp() {
        disputeService = new DisputeService(disputeRepository, orderService);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Pedido pedidoEntregado() {
        Pedido p = new Pedido("Descripción", "Origen", "Destino", "uid-usuario");
        p.setId("pedido-001");
        p.setEstado(EstadoPedido.ENTREGADO);
        p.setRepartidorId("uid-repartidor");
        return p;
    }

    private Disputa disputaAbierta() {
        Disputa d = new Disputa("pedido-001", "uid-usuario", "No llegó completo");
        d.setId("disputa-001");
        return d;
    }

    // ─── abrir() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("abrir: pedido ENTREGADO y usuario dueño → disputa creada")
    void abrir_pedidoEntregadoDueno_creaDisputa() {
        when(orderService.findById("pedido-001")).thenReturn(Optional.of(pedidoEntregado()));
        when(disputeRepository.findByPedidoId("pedido-001")).thenReturn(Optional.empty());
        doNothing().when(disputeRepository).save(any());
        when(orderService.marcarDisputado("pedido-001")).thenReturn(pedidoEntregado());

        Disputa d = disputeService.abrir("pedido-001", "uid-usuario", "No llegó completo");

        assertNotNull(d);
        assertEquals("pedido-001",      d.getPedidoId());
        assertEquals("uid-usuario",     d.getUsuarioId());
        assertEquals("No llegó completo", d.getMotivo());
        assertEquals(EstadoPedido.DISPUTADO, d.getEstado());
        assertNotNull(d.getCreadoEn());
        verify(disputeRepository).save(d);
        verify(orderService).marcarDisputado("pedido-001");
    }

    @Test
    @DisplayName("abrir: pedido inexistente → IllegalArgumentException")
    void abrir_pedidoInexistente_lanzaExcepcion() {
        when(orderService.findById("no-existe")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            disputeService.abrir("no-existe", "uid-usuario", "Motivo"));

        verify(disputeRepository, never()).save(any());
        verify(orderService, never()).marcarDisputado(any());
    }

    @Test
    @DisplayName("abrir: pedido EN_CAMINO (no ENTREGADO) → IllegalStateException")
    void abrir_pedidoNoEntregado_lanzaExcepcion() {
        Pedido p = pedidoEntregado();
        p.setEstado(EstadoPedido.EN_CAMINO);
        when(orderService.findById("pedido-001")).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class, () ->
            disputeService.abrir("pedido-001", "uid-usuario", "Motivo"));

        verify(disputeRepository, never()).save(any());
    }

    @Test
    @DisplayName("abrir: usuario que no es el dueño → IllegalArgumentException")
    void abrir_usuarioNoDueno_lanzaExcepcion() {
        when(orderService.findById("pedido-001")).thenReturn(Optional.of(pedidoEntregado()));

        assertThrows(IllegalArgumentException.class, () ->
            disputeService.abrir("pedido-001", "uid-otro-usuario", "Motivo"));

        verify(disputeRepository, never()).save(any());
    }

    @Test
    @DisplayName("abrir: ya existe disputa para ese pedido → IllegalStateException")
    void abrir_disputaDuplicada_lanzaExcepcion() {
        when(orderService.findById("pedido-001")).thenReturn(Optional.of(pedidoEntregado()));
        when(disputeRepository.findByPedidoId("pedido-001"))
            .thenReturn(Optional.of(disputaAbierta()));

        assertThrows(IllegalStateException.class, () ->
            disputeService.abrir("pedido-001", "uid-usuario", "Motivo"));

        verify(disputeRepository, never()).save(any());
        verify(orderService, never()).marcarDisputado(any());
    }

    // ─── resolver() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolver: disputa DISPUTADO → RESUELTO con resolución registrada")
    void resolver_disputaAbierta_cierraDisputa() {
        Disputa d = disputaAbierta();
        when(disputeRepository.findById("disputa-001")).thenReturn(Optional.of(d));
        when(disputeRepository.update(any())).thenReturn(true);
        when(orderService.marcarResuelto("pedido-001")).thenReturn(new Pedido());

        Disputa resultado = disputeService.resolver("disputa-001", "El repartidor entregó todo");

        assertEquals(EstadoPedido.RESUELTO,         resultado.getEstado());
        assertEquals("El repartidor entregó todo",  resultado.getResolucion());
        assertNotNull(resultado.getResoltoEn());
        verify(disputeRepository).update(d);
        verify(orderService).marcarResuelto("pedido-001");
    }

    @Test
    @DisplayName("resolver: disputa inexistente → IllegalArgumentException")
    void resolver_disputaInexistente_lanzaExcepcion() {
        when(disputeRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            disputeService.resolver("no-existe", "Resolución"));

        verify(disputeRepository, never()).update(any());
        verify(orderService, never()).marcarResuelto(any());
    }

    @Test
    @DisplayName("resolver: disputa ya RESUELTO → IllegalStateException")
    void resolver_disputaYaResuelta_lanzaExcepcion() {
        Disputa d = disputaAbierta();
        d.setEstado(EstadoPedido.RESUELTO);
        when(disputeRepository.findById("disputa-001")).thenReturn(Optional.of(d));

        assertThrows(IllegalStateException.class, () ->
            disputeService.resolver("disputa-001", "Resolución"));

        verify(disputeRepository, never()).update(any());
        verify(orderService, never()).marcarResuelto(any());
    }

    // ─── Consultas ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAbiertas retorna solo disputas en estado DISPUTADO")
    void findAbiertas_retornaDisputasAbiertas() {
        when(disputeRepository.findByEstado(EstadoPedido.DISPUTADO))
            .thenReturn(List.of(disputaAbierta()));

        List<Disputa> resultado = disputeService.findAbiertas();

        assertEquals(1, resultado.size());
        assertEquals(EstadoPedido.DISPUTADO, resultado.get(0).getEstado());
    }

    @Test
    @DisplayName("findByPedidoId retorna la disputa del pedido indicado")
    void findByPedidoId_retornaDisputaCorrecta() {
        when(disputeRepository.findByPedidoId("pedido-001"))
            .thenReturn(Optional.of(disputaAbierta()));

        Optional<Disputa> resultado = disputeService.findByPedidoId("pedido-001");

        assertTrue(resultado.isPresent());
        assertEquals("pedido-001", resultado.get().getPedidoId());
    }

    @Test
    @DisplayName("findByUsuarioId retorna las disputas del usuario")
    void findByUsuarioId_retornaDisputasDelUsuario() {
        when(disputeRepository.findByUsuarioId("uid-usuario"))
            .thenReturn(List.of(disputaAbierta()));

        List<Disputa> resultado = disputeService.findByUsuarioId("uid-usuario");

        assertEquals(1, resultado.size());
        assertEquals("uid-usuario", resultado.get(0).getUsuarioId());
    }
}
