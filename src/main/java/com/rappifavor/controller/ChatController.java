package com.rappifavor.controller;

import com.google.gson.Gson;
import com.rappifavor.config.AuthFilter;
import com.rappifavor.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Map;

import static spark.Spark.get;

/**
 * ChatController — Endpoint REST para acceso al chat de un pedido.
 *
 * Ruta registrada:
 *  GET /api/chats/:pedidoId — Obtener o crear la sala de chat de un pedido (Paso 7.2)
 *
 * Flujo del chat (arquitectura híbrida):
 *  1. El frontend llama a este endpoint con su Firebase JWT.
 *  2. El backend valida que el usuario pertenece al pedido.
 *  3. El backend crea (o retorna) la sala en Firebase Realtime DB.
 *  4. El backend responde con el chatPath.
 *  5. El frontend usa ese chatPath con el Firebase client SDK para
 *     leer y escribir mensajes en tiempo real directamente en RTDB.
 *
 * Respuesta exitosa (200):
 *  {
 *    "pedidoId":     "...",
 *    "chatPath":     "chats/{pedidoId}",
 *    "usuarioId":    "firebase-uid",
 *    "repartidorId": "firebase-uid"
 *  }
 *
 * El frontend escucha mensajes con:
 *  firebase.database().ref(chatPath + "/mensajes").on("value", callback)
 *
 * Códigos de respuesta:
 *  200 OK          — Sala obtenida/creada correctamente
 *  400 Bad Request — Pedido no encontrado o usuario no pertenece al pedido
 *  409 Conflict    — Pedido sin repartidor asignado aún
 *  500 Error       — Fallo al escribir en Firebase Realtime DB
 *
 * Seguridad: requiere token JWT válido (AuthFilter). El frontend queda
 * además sujeto a las Firebase Security Rules del RTDB (firebase-rules.json).
 *
 * Dependencia: → service.ChatService (Paso 7.1)
 */
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private static final Gson gson = new Gson();

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Registra la ruta del chat en Spark.
     * Llamado desde RappiFavorApplication.main() tras registrar los filtros de seguridad.
     */
    public void register() {
        get("/api/chats/:pedidoId", this::getSala);
    }

    // ─── Handler ──────────────────────────────────────────────────────────────

    /**
     * GET /api/chats/:pedidoId — Obtener o crear sala de chat.
     * El uid del solicitante viene del token verificado por AuthFilter.
     */
    private Object getSala(Request req, Response res) {
        try {
            String pedidoId = req.params(":pedidoId");
            String uid      = req.attribute(AuthFilter.ATTR_UID);

            Map<String, String> sala = chatService.obtenerOCrearSala(pedidoId, uid);
            return gson.toJson(sala);

        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            res.status(409);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al obtener sala de chat. pedidoId={}", req.params(":pedidoId"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error al conectar con Firebase Realtime DB."));
        }
    }
}
