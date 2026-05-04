package com.rappifavor.controller;

import com.google.gson.Gson;
import com.rappifavor.config.AuthFilter;
import com.rappifavor.model.RolUsuario;
import com.rappifavor.model.Usuario;
import com.rappifavor.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static spark.Spark.*;

/**
 * UserController — Endpoints REST para gestión de usuarios.
 *
 * Rutas registradas:
 *  POST   /api/users/register         — Registrar nuevo usuario (FR-01)
 *  GET    /api/users/:id              — Obtener usuario por id (FR-02)
 *  GET    /api/users                  — Listar usuarios (FR-10, admin)
 *  PATCH  /api/users/:id/desactivar   — Desactivar cuenta (FR-10, admin)
 *  PATCH  /api/users/:id/activar      — Activar cuenta (FR-10, admin)
 *  PATCH  /api/users/:id/rol          — Cambiar rol (FR-10, admin)
 *
 * Códigos de respuesta:
 *  201 Created           — Usuario registrado exitosamente
 *  200 OK                — Consulta o actualización exitosa
 *  400 Bad Request       — Datos inválidos o regla de negocio violada
 *  404 Not Found         — Usuario no encontrado
 *  500 Internal Error    — Error inesperado del servidor
 *
 * Dependencia: → service.UserService (Paso 4.1)
 */
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private static final Gson gson = new Gson();

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Registra todas las rutas de usuarios en Spark.
     * Llamado desde RappiFavorApplication.main() tras inicializar AppConfig.
     */
    public void register() {
        post("/api/users/register",      this::registrar);
        get("/api/users/:id",            this::getById);
        get("/api/users",                this::getAll);
        patch("/api/users/:id/desactivar", this::desactivar);
        patch("/api/users/:id/activar",    this::activar);
        patch("/api/users/:id/rol",        this::cambiarRol);
    }

    // ─── Handlers ─────────────────────────────────────────────────────────────

    /**
     * POST /api/users/register — Registrar nuevo usuario (FR-01).
     *
     * El Firebase UID se extrae del token verificado por AuthFilter
     * (req.attribute("uid")), NO del body. Esto evita que un cliente
     * registre un perfil con un UID ajeno.
     *
     * Body esperado: { "nombre": "...", "email": "...", "rol": "USUARIO|REPARTIDOR" }
     */
    private Object registrar(Request req, Response res) {
        try {
            if (req.body() == null || req.body().isBlank()) {
                res.status(400);
                return gson.toJson(Map.of("error", "El cuerpo de la solicitud es requerido."));
            }

            // UID proviene del token JWT verificado, no del body (seguridad)
            String firebaseUid = req.attribute(AuthFilter.ATTR_UID);

            @SuppressWarnings("unchecked")
            Map<String, String> body = gson.fromJson(req.body(), Map.class);

            String nombre = body.get("nombre");
            String email  = body.get("email");
            String rolStr = body.get("rol");

            if (nombre == null || email == null || rolStr == null) {
                res.status(400);
                return gson.toJson(Map.of("error",
                        "Campos requeridos: nombre, email, rol."));
            }

            RolUsuario rol = parseRol(rolStr);
            Usuario usuario = userService.registrar(firebaseUid, nombre, email, rol);

            res.status(201);
            return gson.toJson(usuario);

        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al registrar usuario", e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** GET /api/users/:id — Obtener usuario por su Firebase UID. */
    private Object getById(Request req, Response res) {
        try {
            String id = req.params(":id");
            Optional<Usuario> usuario = userService.findById(id);

            if (usuario.isEmpty()) {
                res.status(404);
                return gson.toJson(Map.of("error", "Usuario no encontrado: " + id));
            }

            return gson.toJson(usuario.get());

        } catch (Exception e) {
            logger.error("Error al obtener usuario id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /**
     * GET /api/users — Listar usuarios.
     * Query param opcional: ?rol=USUARIO|REPARTIDOR|ADMINISTRADOR
     */
    private Object getAll(Request req, Response res) {
        try {
            String rolParam = req.queryParams("rol");
            List<Usuario> usuarios;

            if (rolParam != null && !rolParam.isBlank()) {
                RolUsuario rol = parseRol(rolParam);
                usuarios = userService.findByRol(rol);
            } else {
                usuarios = userService.findAll();
            }

            return gson.toJson(usuarios);

        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al listar usuarios", e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** PATCH /api/users/:id/desactivar — Desactivar cuenta (FR-10, admin). */
    private Object desactivar(Request req, Response res) {
        try {
            userService.desactivar(req.params(":id"));
            return gson.toJson(Map.of("mensaje", "Usuario desactivado correctamente."));
        } catch (IllegalArgumentException e) {
            res.status(404);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al desactivar usuario id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** PATCH /api/users/:id/activar — Activar cuenta (FR-10, admin). */
    private Object activar(Request req, Response res) {
        try {
            userService.activar(req.params(":id"));
            return gson.toJson(Map.of("mensaje", "Usuario activado correctamente."));
        } catch (IllegalArgumentException e) {
            res.status(404);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al activar usuario id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    /** PATCH /api/users/:id/rol — Cambiar rol de usuario (FR-10, admin). */
    private Object cambiarRol(Request req, Response res) {
        try {
            if (req.body() == null || req.body().isBlank()) {
                res.status(400);
                return gson.toJson(Map.of("error", "El cuerpo de la solicitud es requerido."));
            }

            @SuppressWarnings("unchecked")
            Map<String, String> body = gson.fromJson(req.body(), Map.class);
            String rolStr = body.get("rol");

            if (rolStr == null) {
                res.status(400);
                return gson.toJson(Map.of("error", "Campo requerido: rol."));
            }

            RolUsuario rol = parseRol(rolStr);
            userService.cambiarRol(req.params(":id"), rol);
            return gson.toJson(Map.of("mensaje", "Rol actualizado correctamente."));

        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al cambiar rol usuario id={}", req.params(":id"), e);
            res.status(500);
            return gson.toJson(Map.of("error", "Error interno del servidor."));
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private RolUsuario parseRol(String rolStr) {
        try {
            return RolUsuario.valueOf(rolStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Rol inválido: '" + rolStr + "'. Valores permitidos: USUARIO, REPARTIDOR, ADMINISTRADOR.");
        }
    }
}
