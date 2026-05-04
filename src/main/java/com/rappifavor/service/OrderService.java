package com.rappifavor.service;

import com.rappifavor.model.EstadoPedido;
import com.rappifavor.model.HistorialEstado;
import com.rappifavor.model.Pedido;
import com.rappifavor.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * OrderService — Lógica de negocio para pedidos.
 *
 * Implementa el AFD del ciclo de vida del pedido (SRS Sección 6.1):
 *
 *   q0 INICIO
 *     → [crear_pedido]     → q1 PENDIENTE
 *   q1 PENDIENTE
 *     → [aceptar_pedido]   → q2 ASIGNADO     (repartidor, FR-05)
 *     → [cancelar_pedido]  → q5 CANCELADO    (usuario)
 *   q2 ASIGNADO
 *     → [iniciar_entrega]  → q3 EN_CAMINO    (repartidor, FR-07)
 *     → [cancelar_pedido]  → q5 CANCELADO    (usuario)
 *   q3 EN_CAMINO
 *     → [confirmar_entrega]→ q4 ENTREGADO    (repartidor, FR-08)
 *   q4 ENTREGADO  (estado final; acepta transición extra)
 *     → [abrir_disputa]    → q6 DISPUTADO    (via DisputeService, FR-09)
 *   q6 DISPUTADO
 *     → [resolver_disputa] → q7 RESUELTO     (via DisputeService, FR-10)
 *   q5 CANCELADO — estado final de aceptación
 *   q7 RESUELTO  — estado final de aceptación
 *
 * Cada transición registra una entrada en Pedido.historial[] (FR-06).
 *
 * Dependencia: → repository.OrderRepository (Paso 3.2)
 */
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // ─── Comandos (transiciones del AFD) ─────────────────────────────────────

    /**
     * Crea un nuevo pedido en estado PENDIENTE (FR-03).
     * Transición del AFD: q0 INICIO → q1 PENDIENTE.
     *
     * La primera entrada del historial registra la entrada a PENDIENTE.
     *
     * @param descripcion Descripción del favor solicitado
     * @param origen      Lugar de recogida
     * @param destino     Lugar de entrega
     * @param usuarioId   Firebase UID del solicitante
     * @return Pedido persistido con id asignado y timestamps
     * @throws IllegalArgumentException si algún campo obligatorio está vacío
     */
    public Pedido crear(String descripcion, String origen, String destino, String usuarioId) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripción del pedido es obligatoria.");
        }
        if (origen == null || origen.isBlank()) {
            throw new IllegalArgumentException("El origen del pedido es obligatorio.");
        }
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("El destino del pedido es obligatorio.");
        }

        String ahora = now();
        Pedido pedido = new Pedido(descripcion, origen, destino, usuarioId);
        pedido.setCreadoEn(ahora);
        pedido.setActualizadoEn(ahora);
        pedido.getHistorial().add(new HistorialEstado(EstadoPedido.PENDIENTE, ahora));

        orderRepository.save(pedido);
        logger.info("Pedido creado. id={} usuarioId={}", pedido.getId(), usuarioId);
        return pedido;
    }

    /**
     * Repartidor acepta el pedido (FR-05).
     * Transición del AFD: q1 PENDIENTE → q2 ASIGNADO.
     *
     * @param pedidoId     Id del pedido a aceptar
     * @param repartidorId Firebase UID del repartidor
     * @return Pedido actualizado
     * @throws IllegalArgumentException si el pedido no existe
     * @throws IllegalStateException    si el pedido no está en PENDIENTE
     */
    public Pedido aceptar(String pedidoId, String repartidorId) {
        Pedido pedido = findByIdOrThrow(pedidoId);
        validarEstado(pedido, EstadoPedido.PENDIENTE, "aceptar");

        String ahora = now();
        pedido.setRepartidorId(repartidorId);
        pedido.setEstado(EstadoPedido.ASIGNADO);
        pedido.setActualizadoEn(ahora);
        pedido.getHistorial().add(new HistorialEstado(EstadoPedido.ASIGNADO, ahora));

        orderRepository.update(pedido);
        logger.info("Pedido aceptado. id={} repartidorId={}", pedidoId, repartidorId);
        return pedido;
    }

    /**
     * Repartidor inicia el traslado (FR-07).
     * Transición del AFD: q2 ASIGNADO → q3 EN_CAMINO.
     *
     * Solo el repartidor asignado puede ejecutar esta acción.
     *
     * @param pedidoId     Id del pedido
     * @param repartidorId Firebase UID del repartidor que inicia el traslado
     * @return Pedido actualizado
     * @throws IllegalArgumentException si el pedido no existe o el repartidor no es el asignado
     * @throws IllegalStateException    si el pedido no está en ASIGNADO
     */
    public Pedido iniciarEntrega(String pedidoId, String repartidorId) {
        Pedido pedido = findByIdOrThrow(pedidoId);
        validarEstado(pedido, EstadoPedido.ASIGNADO, "iniciar entrega");
        validarRepartidorAsignado(pedido, repartidorId);

        String ahora = now();
        pedido.setEstado(EstadoPedido.EN_CAMINO);
        pedido.setActualizadoEn(ahora);
        pedido.getHistorial().add(new HistorialEstado(EstadoPedido.EN_CAMINO, ahora));

        orderRepository.update(pedido);
        logger.info("Entrega iniciada. id={}", pedidoId);
        return pedido;
    }

    /**
     * Repartidor confirma la entrega (FR-08).
     * Transición del AFD: q3 EN_CAMINO → q4 ENTREGADO.
     *
     * Solo el repartidor asignado puede confirmar la entrega.
     *
     * @param pedidoId     Id del pedido
     * @param repartidorId Firebase UID del repartidor que confirma
     * @return Pedido actualizado
     * @throws IllegalArgumentException si el pedido no existe o el repartidor no es el asignado
     * @throws IllegalStateException    si el pedido no está en EN_CAMINO
     */
    public Pedido confirmarEntrega(String pedidoId, String repartidorId) {
        Pedido pedido = findByIdOrThrow(pedidoId);
        validarEstado(pedido, EstadoPedido.EN_CAMINO, "confirmar entrega");
        validarRepartidorAsignado(pedido, repartidorId);

        String ahora = now();
        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.setActualizadoEn(ahora);
        pedido.getHistorial().add(new HistorialEstado(EstadoPedido.ENTREGADO, ahora));

        orderRepository.update(pedido);
        logger.info("Entrega confirmada. id={}", pedidoId);
        return pedido;
    }

    /**
     * Usuario cancela el pedido.
     * Transiciones válidas del AFD:
     *   q1 PENDIENTE → q5 CANCELADO
     *   q2 ASIGNADO  → q5 CANCELADO
     *
     * Solo el usuario que creó el pedido puede cancelarlo.
     *
     * @param pedidoId  Id del pedido a cancelar
     * @param usuarioId Firebase UID del usuario que cancela
     * @return Pedido actualizado
     * @throws IllegalArgumentException si el pedido no existe o el usuario no es el dueño
     * @throws IllegalStateException    si el pedido no está en un estado cancelable
     */
    public Pedido cancelar(String pedidoId, String usuarioId) {
        Pedido pedido = findByIdOrThrow(pedidoId);

        EstadoPedido estadoActual = pedido.getEstado();
        if (estadoActual != EstadoPedido.PENDIENTE && estadoActual != EstadoPedido.ASIGNADO) {
            throw new IllegalStateException(
                    "Solo se puede cancelar un pedido PENDIENTE o ASIGNADO. Estado actual: " + estadoActual);
        }
        if (!pedido.getUsuarioId().equals(usuarioId)) {
            throw new IllegalArgumentException("Solo el usuario que creó el pedido puede cancelarlo.");
        }

        String ahora = now();
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedido.setActualizadoEn(ahora);
        pedido.getHistorial().add(new HistorialEstado(EstadoPedido.CANCELADO, ahora));

        orderRepository.update(pedido);
        logger.info("Pedido cancelado. id={} usuarioId={}", pedidoId, usuarioId);
        return pedido;
    }

    /**
     * Transiciona el pedido a DISPUTADO (FR-09).
     * Transición del AFD: q4 ENTREGADO → q6 DISPUTADO.
     *
     * Este método es llamado exclusivamente por DisputeService.abrir()
     * después de crear el documento de disputa. No se llama directamente
     * desde el controller.
     *
     * @param pedidoId Id del pedido a marcar como disputado
     * @return Pedido actualizado
     * @throws IllegalArgumentException si el pedido no existe
     * @throws IllegalStateException    si el pedido no está en ENTREGADO
     */
    public Pedido marcarDisputado(String pedidoId) {
        Pedido pedido = findByIdOrThrow(pedidoId);
        validarEstado(pedido, EstadoPedido.ENTREGADO, "abrir disputa");

        String ahora = now();
        pedido.setEstado(EstadoPedido.DISPUTADO);
        pedido.setActualizadoEn(ahora);
        pedido.getHistorial().add(new HistorialEstado(EstadoPedido.DISPUTADO, ahora));

        orderRepository.update(pedido);
        logger.info("Pedido marcado como DISPUTADO. id={}", pedidoId);
        return pedido;
    }

    /**
     * Transiciona el pedido a RESUELTO (FR-10).
     * Transición del AFD: q6 DISPUTADO → q7 RESUELTO.
     *
     * Este método es llamado exclusivamente por DisputeService.resolver()
     * después de registrar la resolución del administrador.
     *
     * @param pedidoId Id del pedido a marcar como resuelto
     * @return Pedido actualizado
     * @throws IllegalArgumentException si el pedido no existe
     * @throws IllegalStateException    si el pedido no está en DISPUTADO
     */
    public Pedido marcarResuelto(String pedidoId) {
        Pedido pedido = findByIdOrThrow(pedidoId);
        validarEstado(pedido, EstadoPedido.DISPUTADO, "resolver disputa");

        String ahora = now();
        pedido.setEstado(EstadoPedido.RESUELTO);
        pedido.setActualizadoEn(ahora);
        pedido.getHistorial().add(new HistorialEstado(EstadoPedido.RESUELTO, ahora));

        orderRepository.update(pedido);
        logger.info("Pedido marcado como RESUELTO. id={}", pedidoId);
        return pedido;
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    /**
     * Busca un pedido por su id.
     *
     * @param id ObjectId hex string del pedido
     * @return Optional con el pedido si existe, vacío si no
     */
    public Optional<Pedido> findById(String id) {
        return orderRepository.findById(id);
    }

    /**
     * Retorna todos los pedidos de la plataforma (FR-11, admin).
     *
     * @return Lista de pedidos, vacía si no hay ninguno
     */
    public List<Pedido> findAll() {
        return orderRepository.findAll();
    }

    /**
     * Retorna todos los pedidos de un usuario (FR-04).
     *
     * @param usuarioId Firebase UID del usuario solicitante
     * @return Lista de pedidos del usuario, vacía si no tiene ninguno
     */
    public List<Pedido> findByUsuarioId(String usuarioId) {
        return orderRepository.findByUsuarioId(usuarioId);
    }

    /**
     * Retorna todos los pedidos de un repartidor.
     *
     * @param repartidorId Firebase UID del repartidor
     * @return Lista de pedidos del repartidor, vacía si no tiene ninguno
     */
    public List<Pedido> findByRepartidorId(String repartidorId) {
        return orderRepository.findByRepartidorId(repartidorId);
    }

    /**
     * Retorna todos los pedidos en un estado específico del AFD.
     * Caso de uso principal: listar pedidos PENDIENTE para repartidores (FR-05).
     *
     * @param estado Estado del AFD a filtrar
     * @return Lista de pedidos en ese estado, vacía si no hay ninguno
     */
    public List<Pedido> findByEstado(EstadoPedido estado) {
        return orderRepository.findByEstado(estado);
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private Pedido findByIdOrThrow(String pedidoId) {
        return orderRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));
    }

    /**
     * Verifica que el pedido esté en el estado esperado para la transición.
     * Si no, lanza IllegalStateException con mensaje descriptivo.
     */
    private void validarEstado(Pedido pedido, EstadoPedido estadoEsperado, String accion) {
        if (pedido.getEstado() != estadoEsperado) {
            throw new IllegalStateException(String.format(
                    "No se puede %s: el pedido está en %s, se requiere %s.",
                    accion, pedido.getEstado(), estadoEsperado));
        }
    }

    /**
     * Verifica que el repartidor que intenta operar sea el asignado al pedido.
     */
    private void validarRepartidorAsignado(Pedido pedido, String repartidorId) {
        if (!repartidorId.equals(pedido.getRepartidorId())) {
            throw new IllegalArgumentException(
                    "Solo el repartidor asignado puede realizar esta acción.");
        }
    }

    private String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
