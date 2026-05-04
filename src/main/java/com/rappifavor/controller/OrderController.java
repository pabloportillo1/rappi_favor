package com.rappifavor.controller;

import com.google.gson.Gson;
import com.rappifavor.model.EstadoPedido;
import com.rappifavor.model.Pedido;
import com.rappifavor.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static spark.Spark.*;

/**
 * OrderController — Endpoints REST para gestión de pedidos.
 *
 * Rutas registradas:
 *  POST   /api/orders                     — Crear pedido (FR-03)
 *  GET    /api/orders                     — Listar pedidos (FR-04, FR-11)
 *  GET    /api/orders/:id                 — Obtener pedido por id
 *  PATCH  /api/orders/:id/aceptar         — Repartidor acepta pedido (FR-05)
 *  PATCH  /api/orders/:id/iniciar         — Repartidor inicia entrega (FR-07)
 *  PATCH  /api/orders/:id/entregar        — Repartidor confirma entrega (FR-08)
 *  PATCH  /api/orders/:id/cancelar        — Usuario cancela pedido
 *
 * Query params para GET /api/orders:
 *  ?estado=PENDIENTE|ASIGNADO|...     — filtra por estado del AFD
 *  ?usuarioId=xxx                     — filtra por usuario solicitante
 *  ?repartidorId=xxx                  — filtra por repartidor
 *
 * Códigos de respuesta:
 *  201 Created           — Pedido creado exitosamente
 *  200 OK                — Consulta o transición exitosa
 *  400 Bad Request       — Datos inválidos
 *  404 Not Found         — Pedido no encontrado
 *  409 Conflict          — Transición de estado inválida (AFD)
 *  500 Internal Error    — Error inesperado del servidor
 *
 * Dependencia: → service.OrderService (Paso 4.2)
 */
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private static final Gson gson = new Gson();

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Registra todas las rutas de pedidos en Spark.
     * Llamado desde RappiFavorApplication.main() tras inicializar AppConfig.
     */
    public void register() {
        post("/api/orders",                    this::crear);
        get("/api/orders",                     this::getAll);
        get("/api/orders/:id",                 this::getById);
        patch("/api/orders/:id/aceptar",       this::aceptar);
        patch("/api/orders/:id/iniciar",       this::iniciarEntrega);
        patch("/api/orders/:id/entregar",      this::confirmarEntrega);
        patch("/api/orders/:id/cancelar",      this::cancelar);
    }

    // ─── Handlers ─────────────────────────────────────────────────────────────

    /** POST /api/orders — Crear nuevo pedido en estado PENDIENTE (FR-03). */
    private Object crear(Request req, Response res) {
        try {
            if (req.body() == null || req.body().isBlank()) {
                res.status(400);
                return gson.toJson(Map.of("error", "El cuerpo de la solicitud es requerido."));
            }

            @SuppressWarnings("unchecked")
            Map<String, String> body = gson.fromJson(req.body(), Map.class);

            String descripcion = body.get("descripcion");
            String origen      = body.get("origen");
            String destino     = body.get("destino");
            String usuarioId   = body.get("usuarioId");

            if (descripcion == null || origen == null || destino == null || usuarioId == null) {
                res.status(400);
                return gson.toJson(Map.of("error",
                        "Campos requeridos: descripcion, origen, destino, usuarioId."));
            }

            Pedido pedido = orderService.crear(descripcion, origen, destino, usuarioId);
            res.status(201);
            return gson.toJson(pedido);

        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al crear pedido", e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /**
     * GET /api/orders — Listar pedidos con filtros opcionales.
     * ?estado=PENDIENTE  — para que repartidores vean pedidos disponibles (FR-05).
     * ?usuarioId=xxx     — historial de pedidos del usuario (FR-04).
     * ?repartidorId=xxx  — historial de pedidos del repartidor.
     * Sin params         — todos los pedidos (admin, FR-11).
     */
    private Object getAll(Request req, Response res) {
        try {
            String estadoParam      = req.queryParams("estado");
            String usuarioIdParam   = req.queryParams("usuarioId");
            String repartidorIdParam = req.queryParams("repartidorId");

            List<Pedido> pedidos;

            if (estadoParam != null && !estadoParam.isBlank()) {
                EstadoPedido estado = parseEstado(estadoParam);
                pedidos = orderService.findByEstado(estado);
            } else if (usuarioIdParam != null && !usuarioIdParam.isBlank()) {
                pedidos = orderService.findByUsuarioId(usuarioIdParam);
            } else if (repartidorIdParam != null && !repartidorIdParam.isBlank()) {
                pedidos = orderService.findByRepartidorId(repartidorIdParam);
            } else {
                pedidos = orderService.findAll();
            }

            return gson.toJson(pedidos);

        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al listar pedidos", e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** GET /api/orders/:id — Obtener pedido por id con su historial de estados. */
    private Object getById(Request req, Response res) {
        try {
            String id = req.params(":id");
            Optional<Pedido> pedido = orderService.findById(id);

            if (pedido.isEmpty()) {
                res.status(404);
                return gson.toJson(Map.of("error", "Pedido no encontrado: " + id));
            }

            return gson.toJson(pedido.get());

        } catch (Exception e) {
            logger.error("Error al obtener pedido id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /**
     * PATCH /api/orders/:id/aceptar — Repartidor acepta el pedido (FR-05).
     * AFD: PENDIENTE → ASIGNADO
     * Body: { "repartidorId": "uid" }
     */
    private Object aceptar(Request req, Response res) {
        try {
            String repartidorId = extraerCampo(req, "repartidorId");
            Pedido pedido = orderService.aceptar(req.params(":id"), repartidorId);
            return gson.toJson(pedido);
        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            res.status(409);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al aceptar pedido id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /**
     * PATCH /api/orders/:id/iniciar — Repartidor inicia el traslado (FR-07).
     * AFD: ASIGNADO → EN_CAMINO
     * Body: { "repartidorId": "uid" }
     */
    private Object iniciarEntrega(Request req, Response res) {
        try {
            String repartidorId = extraerCampo(req, "repartidorId");
            Pedido pedido = orderService.iniciarEntrega(req.params(":id"), repartidorId);
            return gson.toJson(pedido);
        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            res.status(409);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al iniciar entrega pedido id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /**
     * PATCH /api/orders/:id/entregar — Repartidor confirma la entrega (FR-08).
     * AFD: EN_CAMINO → ENTREGADO
     * Body: { "repartidorId": "uid" }
     */
    private Object confirmarEntrega(Request req, Response res) {
        try {
            String repartidorId = extraerCampo(req, "repartidorId");
            Pedido pedido = orderService.confirmarEntrega(req.params(":id"), repartidorId);
            return gson.toJson(pedido);
        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            res.status(409);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al confirmar entrega pedido id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /**
     * PATCH /api/orders/:id/cancelar — Usuario cancela el pedido.
     * AFD: PENDIENTE|ASIGNADO → CANCELADO
     * Body: { "usuarioId": "uid" }
     */
    private Object cancelar(Request req, Response res) {
        try {
            String usuarioId = extraerCampo(req, "usuarioId");
            Pedido pedido = orderService.cancelar(req.params(":id"), usuarioId);
            return gson.toJson(pedido);
        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            res.status(409);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al cancelar pedido id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    /**
     * Extrae un campo String del body JSON.
     * @throws IllegalArgumentException si el body está vacío o el campo no existe
     */
    private String extraerCampo(Request req, String campo) {
        if (req.body() == null || req.body().isBlank()) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es requerido.");
        }
        @SuppressWarnings("unchecked")
        Map<String, String> body = gson.fromJson(req.body(), Map.class);
        String valor = body.get(campo);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Campo requerido: " + campo + ".");
        }
        return valor;
    }

    private EstadoPedido parseEstado(String estadoStr) {
        try {
            return EstadoPedido.valueOf(estadoStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Estado inválido: '" + estadoStr + "'. Valores permitidos: " +
                    "PENDIENTE, ASIGNADO, EN_CAMINO, ENTREGADO, CANCELADO, DISPUTADO, RESUELTO.");
        }
    }
}
