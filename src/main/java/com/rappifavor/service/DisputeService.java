package com.rappifavor.service;

import com.rappifavor.model.Disputa;
import com.rappifavor.model.EstadoPedido;
import com.rappifavor.model.Pedido;
import com.rappifavor.repository.DisputeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * DisputeService — Lógica de negocio para la gestión de disputas.
 *
 * Responsabilidades:
 *  - Abrir una disputa sobre un pedido ENTREGADO (FR-09).
 *  - Resolver una disputa y marcar el pedido como RESUELTO (FR-10).
 *  - Coordinar con OrderService para las transiciones del AFD:
 *      q4 ENTREGADO  → q6 DISPUTADO   (abrir_disputa)
 *      q6 DISPUTADO  → q7 RESUELTO    (resolver_disputa)
 *
 * Este servicio es el único autorizado para invocar
 * OrderService.marcarDisputado() y OrderService.marcarResuelto().
 *
 * Requerimientos: FR-09 (abrir disputa), FR-10 (resolver disputa, admin).
 *
 * Dependencias:
 *  → repository.DisputeRepository (Paso 3.3)
 *  → service.OrderService         (Paso 4.2)
 */
public class DisputeService {

    private static final Logger logger = LoggerFactory.getLogger(DisputeService.class);

    private final DisputeRepository disputeRepository;
    private final OrderService orderService;

    public DisputeService(DisputeRepository disputeRepository, OrderService orderService) {
        this.disputeRepository = disputeRepository;
        this.orderService = orderService;
    }

    // ─── Comandos ─────────────────────────────────────────────────────────────

    /**
     * Abre una disputa sobre un pedido entregado (FR-09).
     *
     * Reglas de negocio:
     *  1. El pedido debe existir y estar en estado ENTREGADO.
     *  2. Solo el usuario que solicitó el pedido puede abrir la disputa.
     *  3. No puede haber ya una disputa activa para el mismo pedido.
     *
     * Secuencia atómica a nivel de negocio:
     *  a. Crea el documento Disputa en la colección "disputas".
     *  b. Transiciona el pedido a DISPUTADO (AFD: q4 → q6).
     *
     * @param pedidoId  Id del pedido sobre el que se abre la disputa
     * @param usuarioId Firebase UID del usuario que abre la disputa
     * @param motivo    Descripción del motivo de la controversia
     * @return Disputa creada con id asignado
     * @throws IllegalArgumentException si el pedido no existe, el usuario no es el dueño,
     *                                  o ya existe una disputa para ese pedido
     * @throws IllegalStateException    si el pedido no está en ENTREGADO
     */
    public Disputa abrir(String pedidoId, String usuarioId, String motivo) {
        // 1. Verificar que el pedido existe y está ENTREGADO
        Pedido pedido = orderService.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        if (pedido.getEstado() != EstadoPedido.ENTREGADO) {
            throw new IllegalStateException(
                    "Solo se puede disputar un pedido ENTREGADO. Estado actual: " + pedido.getEstado());
        }

        // 2. Verificar que el usuario es el dueño del pedido
        if (!pedido.getUsuarioId().equals(usuarioId)) {
            throw new IllegalArgumentException(
                    "Solo el usuario que solicitó el pedido puede abrir una disputa.");
        }

        // 3. Verificar que no exista ya una disputa para este pedido
        if (disputeRepository.findByPedidoId(pedidoId).isPresent()) {
            throw new IllegalStateException(
                    "Ya existe una disputa para el pedido: " + pedidoId);
        }

        // 4. Crear el documento de disputa
        Disputa disputa = new Disputa(pedidoId, usuarioId, motivo);
        disputa.setCreadoEn(now());
        disputeRepository.save(disputa);

        // 5. Transicionar el pedido a DISPUTADO (AFD: q4 ENTREGADO → q6 DISPUTADO)
        orderService.marcarDisputado(pedidoId);

        logger.info("Disputa abierta. id={} pedidoId={} usuarioId={}", disputa.getId(), pedidoId, usuarioId);
        return disputa;
    }

    /**
     * El administrador resuelve una disputa (FR-10).
     *
     * Reglas de negocio:
     *  1. La disputa debe existir y estar en estado DISPUTADO.
     *  2. Registra la resolución y la fecha de cierre.
     *  3. Transiciona el pedido a RESUELTO (AFD: q6 → q7).
     *
     * Secuencia atómica a nivel de negocio:
     *  a. Actualiza la Disputa: resolucion, estado=RESUELTO, resoltoEn.
     *  b. Transiciona el pedido a RESUELTO (AFD: q6 → q7).
     *
     * @param disputaId  Id de la disputa a resolver
     * @param resolucion Texto de la resolución emitida por el administrador
     * @return Disputa actualizada con la resolución registrada
     * @throws IllegalArgumentException si la disputa no existe
     * @throws IllegalStateException    si la disputa no está en DISPUTADO
     */
    public Disputa resolver(String disputaId, String resolucion) {
        // 1. Verificar que la disputa existe y está abierta
        Disputa disputa = disputeRepository.findById(disputaId)
                .orElseThrow(() -> new IllegalArgumentException("Disputa no encontrada: " + disputaId));

        if (disputa.getEstado() != EstadoPedido.DISPUTADO) {
            throw new IllegalStateException(
                    "Solo se puede resolver una disputa en estado DISPUTADO. Estado actual: " + disputa.getEstado());
        }

        // 2. Registrar resolución en la disputa
        String ahora = now();
        disputa.setResolucion(resolucion);
        disputa.setEstado(EstadoPedido.RESUELTO);
        disputa.setResoltoEn(ahora);
        disputeRepository.update(disputa);

        // 3. Transicionar el pedido a RESUELTO (AFD: q6 DISPUTADO → q7 RESUELTO)
        orderService.marcarResuelto(disputa.getPedidoId());

        logger.info("Disputa resuelta. id={} pedidoId={}", disputaId, disputa.getPedidoId());
        return disputa;
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    /**
     * Busca una disputa por su id.
     *
     * @param id ObjectId hex string de la disputa
     * @return Optional con la disputa si existe, vacío si no
     */
    public Optional<Disputa> findById(String id) {
        return disputeRepository.findById(id);
    }

    /**
     * Busca la disputa asociada a un pedido.
     * Un pedido tiene como máximo una disputa en el MVP.
     *
     * @param pedidoId Id del pedido
     * @return Optional con la disputa si existe, vacío si ese pedido no tiene disputa
     */
    public Optional<Disputa> findByPedidoId(String pedidoId) {
        return disputeRepository.findByPedidoId(pedidoId);
    }

    /**
     * Retorna todas las disputas abiertas por un usuario (FR-09).
     *
     * @param usuarioId Firebase UID del usuario
     * @return Lista de disputas del usuario, vacía si no tiene ninguna
     */
    public List<Disputa> findByUsuarioId(String usuarioId) {
        return disputeRepository.findByUsuarioId(usuarioId);
    }

    /**
     * Retorna todas las disputas pendientes de resolución (FR-10, admin).
     * Devuelve las disputas en estado DISPUTADO.
     *
     * @return Lista de disputas abiertas, vacía si no hay ninguna
     */
    public List<Disputa> findAbiertas() {
        return disputeRepository.findByEstado(EstadoPedido.DISPUTADO);
    }

    /**
     * Retorna todas las disputas de la plataforma (FR-10, admin).
     *
     * @return Lista de todas las disputas, vacía si no hay ninguna
     */
    public List<Disputa> findAll() {
        return disputeRepository.findAll();
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
