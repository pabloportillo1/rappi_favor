package com.rappifavor.controller;

import com.google.gson.Gson;
import com.rappifavor.model.Disputa;
import com.rappifavor.service.DisputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static spark.Spark.*;

/**
 * DisputeController — Endpoints REST para gestión de disputas.
 *
 * Rutas registradas:
 *  POST   /api/disputes                        — Abrir disputa (FR-09)
 *  GET    /api/disputes/abiertas               — Listar disputas abiertas (FR-10, admin)
 *  GET    /api/disputes/usuario/:usuarioId     — Disputas de un usuario (FR-09)
 *  GET    /api/disputes/:id                    — Obtener disputa por id
 *  GET    /api/disputes                        — Todas las disputas (FR-10, admin)
 *  PATCH  /api/disputes/:id/resolver           — Resolver disputa (FR-10, admin)
 *
 * IMPORTANTE — orden de registro:
 *  Las rutas con segmentos fijos (/abiertas, /usuario/:uid) se registran
 *  ANTES que /api/disputes/:id para evitar que Spark las interprete como :id.
 *
 * Códigos de respuesta:
 *  201 Created           — Disputa abierta exitosamente
 *  200 OK                — Consulta o resolución exitosa
 *  400 Bad Request       — Datos inválidos o regla de negocio violada
 *  404 Not Found         — Disputa no encontrada
 *  409 Conflict          — Estado inválido (disputa ya resuelta, pedido no ENTREGADO, etc.)
 *  500 Internal Error    — Error inesperado del servidor
 *
 * Dependencia: → service.DisputeService (Paso 4.3)
 */
public class DisputeController {

    private static final Logger logger = LoggerFactory.getLogger(DisputeController.class);
    private static final Gson gson = new Gson();

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /**
     * Registra todas las rutas de disputas en Spark.
     * Las rutas específicas se registran antes que las parametrizadas.
     */
    public void register() {
        post("/api/disputes",                           this::abrir);
        get("/api/disputes/abiertas",                   this::getAbiertas);
        get("/api/disputes/usuario/:usuarioId",         this::getByUsuario);
        get("/api/disputes/:id",                        this::getById);
        get("/api/disputes",                            this::getAll);
        patch("/api/disputes/:id/resolver",             this::resolver);
    }

    // ─── Handlers ─────────────────────────────────────────────────────────────

    /**
     * POST /api/disputes — Abrir una disputa sobre un pedido ENTREGADO (FR-09).
     * Body: { "pedidoId": "...", "usuarioId": "...", "motivo": "..." }
     */
    private Object abrir(Request req, Response res) {
        try {
            if (req.body() == null || req.body().isBlank()) {
                res.status(400);
                return gson.toJson(Map.of("error", "El cuerpo de la solicitud es requerido."));
            }

            @SuppressWarnings("unchecked")
            Map<String, String> body = gson.fromJson(req.body(), Map.class);

            String pedidoId  = body.get("pedidoId");
            String usuarioId = body.get("usuarioId");
            String motivo    = body.get("motivo");

            if (pedidoId == null || usuarioId == null || motivo == null) {
                res.status(400);
                return gson.toJson(Map.of("error",
                        "Campos requeridos: pedidoId, usuarioId, motivo."));
            }

            Disputa disputa = disputeService.abrir(pedidoId, usuarioId, motivo);
            res.status(201);
            return gson.toJson(disputa);

        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            res.status(409);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al abrir disputa", e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** GET /api/disputes/abiertas — Disputas pendientes de resolución (FR-10, admin). */
    private Object getAbiertas(Request req, Response res) {
        try {
            List<Disputa> disputas = disputeService.findAbiertas();
            return gson.toJson(disputas);
        } catch (Exception e) {
            logger.error("Error al listar disputas abiertas", e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** GET /api/disputes/usuario/:usuarioId — Disputas de un usuario (FR-09). */
    private Object getByUsuario(Request req, Response res) {
        try {
            String usuarioId = req.params(":usuarioId");
            List<Disputa> disputas = disputeService.findByUsuarioId(usuarioId);
            return gson.toJson(disputas);
        } catch (Exception e) {
            logger.error("Error al obtener disputas de usuario id={}", req.params(":usuarioId"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** GET /api/disputes/:id — Obtener disputa por id. */
    private Object getById(Request req, Response res) {
        try {
            String id = req.params(":id");
            Optional<Disputa> disputa = disputeService.findById(id);

            if (disputa.isEmpty()) {
                res.status(404);
                return gson.toJson(Map.of("error", "Disputa no encontrada: " + id));
            }

            return gson.toJson(disputa.get());

        } catch (Exception e) {
            logger.error("Error al obtener disputa id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** GET /api/disputes — Todas las disputas de la plataforma (FR-10, admin). */
    private Object getAll(Request req, Response res) {
        try {
            List<Disputa> disputas = disputeService.findAll();
            return gson.toJson(disputas);
        } catch (Exception e) {
            logger.error("Error al listar disputas", e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /**
     * PATCH /api/disputes/:id/resolver — Administrador resuelve la disputa (FR-10).
     * AFD pedido: DISPUTADO → RESUELTO
     * Body: { "resolucion": "Texto de la resolución..." }
     */
    private Object resolver(Request req, Response res) {
        try {
            if (req.body() == null || req.body().isBlank()) {
                res.status(400);
                return gson.toJson(Map.of("error", "El cuerpo de la solicitud es requerido."));
            }

            @SuppressWarnings("unchecked")
            Map<String, String> body = gson.fromJson(req.body(), Map.class);
            String resolucion = body.get("resolucion");

            if (resolucion == null || resolucion.isBlank()) {
                res.status(400);
                return gson.toJson(Map.of("error", "Campo requerido: resolucion."));
            }

            Disputa disputa = disputeService.resolver(req.params(":id"), resolucion);
            return gson.toJson(disputa);

        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            res.status(409);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al resolver disputa id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }
}
