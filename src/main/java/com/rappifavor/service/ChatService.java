package com.rappifavor.service;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.rappifavor.model.EstadoPedido;
import com.rappifavor.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ChatService — Gestión de salas de chat en Firebase Realtime Database.
 *
 * Arquitectura del chat (Paso 7.1):
 *
 *   Backend (este servicio)
 *     → Crea la sala y escribe los metadatos (Admin SDK, sin Security Rules).
 *     → Valida que solo el usuario y el repartidor del pedido accedan.
 *     → Devuelve el chatPath al controller.
 *
 *   Frontend (fuera de este backend)
 *     → Usa el Firebase client SDK para leer y escribir mensajes directamente.
 *     → Está sujeto a las Firebase Security Rules (ver firebase-rules.json).
 *
 * Estructura en Realtime DB:
 *   chats/
 *     {pedidoId}/
 *       pedidoId:     "..."
 *       usuarioId:    "firebase-uid"
 *       repartidorId: "firebase-uid"
 *       creadoEn:     "ISO 8601"
 *       mensajes/
 *         {auto-id}/
 *           texto:     "..."
 *           autorId:   "firebase-uid"
 *           timestamp: 1234567890
 *
 * El chat solo existe cuando el pedido tiene repartidor asignado.
 * Los estados válidos son: ASIGNADO, EN_CAMINO, ENTREGADO, DISPUTADO.
 *
 * Requerimientos: FR-Chat (Paso 7).
 * Dependencia: → service.OrderService (Paso 4.2)
 */
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    /** Estados del pedido en los que el chat tiene sentido (repartidor ya asignado). */
    private static final Set<EstadoPedido> ESTADOS_CON_CHAT = Set.of(
            EstadoPedido.ASIGNADO,
            EstadoPedido.EN_CAMINO,
            EstadoPedido.ENTREGADO,
            EstadoPedido.DISPUTADO
    );

    private final OrderService orderService;

    public ChatService(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Obtiene o crea la sala de chat para un pedido (FR-Chat).
     *
     * Reglas de negocio:
     *  1. El pedido debe existir.
     *  2. El pedido debe tener repartidor asignado (estado ASIGNADO o posterior).
     *  3. El solicitante debe ser el usuario o el repartidor del pedido.
     *
     * Si la sala ya existe en RTDB, updateChildrenAsync actualiza solo los
     * metadatos sin tocar los mensajes existentes.
     *
     * @param pedidoId       Id del pedido cuya sala se quiere obtener/crear
     * @param solicitanteUid Firebase UID del usuario que pide acceso al chat
     * @return Mapa con pedidoId, chatPath, usuarioId y repartidorId
     * @throws IllegalArgumentException si el pedido no existe o el usuario no pertenece al pedido
     * @throws IllegalStateException    si el pedido no tiene repartidor asignado aún
     * @throws RuntimeException         si falla la escritura en Firebase Realtime DB
     */
    public Map<String, String> obtenerOCrearSala(String pedidoId, String solicitanteUid) {

        // 1. Verificar que el pedido existe
        Pedido pedido = orderService.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));

        // 2. Verificar que el pedido está en un estado con chat activo
        if (!ESTADOS_CON_CHAT.contains(pedido.getEstado())) {
            throw new IllegalStateException(
                    "El chat solo está disponible cuando el pedido tiene un repartidor asignado. " +
                    "Estado actual: " + pedido.getEstado() + ". " +
                    "Estados válidos: ASIGNADO, EN_CAMINO, ENTREGADO, DISPUTADO.");
        }

        // 3. Verificar que el solicitante es el usuario o el repartidor del pedido
        boolean esUsuario     = pedido.getUsuarioId().equals(solicitanteUid);
        boolean esRepartidor  = solicitanteUid.equals(pedido.getRepartidorId());

        if (!esUsuario && !esRepartidor) {
            throw new IllegalArgumentException(
                    "Solo el usuario o el repartidor del pedido pueden acceder al chat.");
        }

        // 4. Crear o actualizar la sala en Firebase Realtime DB
        // El Admin SDK bypasea las Security Rules — solo el backend puede escribir metadatos.
        // updateChildrenAsync: merge parcial — no sobreescribe los mensajes existentes.
        String chatPath = "chats/" + pedidoId;
        DatabaseReference chatRef = FirebaseDatabase.getInstance().getReference(chatPath);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pedidoId",     pedidoId);
        metadata.put("usuarioId",    pedido.getUsuarioId());
        metadata.put("repartidorId", pedido.getRepartidorId());
        metadata.put("creadoEn",     now());

        try {
            chatRef.updateChildrenAsync(metadata).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operación interrumpida al crear sala de chat.", e);
        } catch (Exception e) {
            throw new RuntimeException("Error al escribir en Firebase Realtime DB: " + e.getMessage(), e);
        }

        logger.info("Sala de chat lista. pedidoId={} solicitante={}", pedidoId, solicitanteUid);

        // 5. Devolver la referencia para que el frontend se conecte directamente a RTDB
        Map<String, String> sala = new HashMap<>();
        sala.put("pedidoId",     pedidoId);
        sala.put("chatPath",     chatPath);
        sala.put("usuarioId",    pedido.getUsuarioId());
        sala.put("repartidorId", pedido.getRepartidorId());
        return sala;
    }

    private String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
