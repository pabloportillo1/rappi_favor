package com.rappifavor.config;

import com.google.gson.Gson;
import com.rappifavor.model.RolUsuario;
import com.rappifavor.model.Usuario;
import com.rappifavor.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Map;
import java.util.Optional;

import static spark.Spark.before;
import static spark.Spark.halt;

/**
 * RoleFilter — Control de acceso basado en roles para rutas protegidas.
 *
 * Responsabilidades:
 *  - Leer el uid establecido por AuthFilter (req.attribute("uid")).
 *  - Consultar el rol del usuario en MongoDB.
 *  - Denegar acceso (HTTP 403) si el rol no está autorizado para la ruta.
 *
 * Reglas de acceso por ruta:
 *
 *  ADMINISTRADOR:
 *   GET  /api/users                     — listar todos los usuarios (FR-10)
 *   PATCH /api/users/:id/desactivar     — desactivar cuenta (FR-10)
 *   PATCH /api/users/:id/activar        — activar cuenta (FR-10)
 *   PATCH /api/users/:id/rol            — cambiar rol (FR-10)
 *   GET  /api/disputes                  — todas las disputas (FR-10)
 *   GET  /api/disputes/abiertas         — disputas pendientes (FR-10)
 *   PATCH /api/disputes/:id/resolver    — resolver disputa (FR-10)
 *
 *  REPARTIDOR:
 *   PATCH /api/orders/:id/aceptar       — aceptar pedido (FR-05)
 *   PATCH /api/orders/:id/iniciar       — iniciar entrega (FR-07)
 *   PATCH /api/orders/:id/entregar      — confirmar entrega (FR-08)
 *
 * Rutas con método mixto (mismo path, distintos métodos, distinto rol):
 *  POST /api/disputes  — cualquier usuario autenticado (abrir disputa, FR-09)
 *  GET  /api/disputes  — solo ADMINISTRADOR
 *  → El filtro de /api/disputes verifica el método HTTP antes de aplicar el rol.
 *
 * Precondición: AuthFilter debe haberse aplicado antes (ATTR_UID disponible).
 *
 * Dependencia: → service.UserService (Paso 4.1)
 * Referencia: FR-05, FR-07, FR-08, FR-09, FR-10, Paso 6.2.
 */
public class RoleFilter {

    private static final Logger logger = LoggerFactory.getLogger(RoleFilter.class);
    private static final Gson gson = new Gson();

    private final UserService userService;

    public RoleFilter(UserService userService) {
        this.userService = userService;
    }

    /**
     * Registra los filtros de rol en Spark.
     * Debe llamarse DESPUÉS de AuthFilter.apply() y ANTES de los controllers.
     */
    public void apply() {

        // ─── Rutas exclusivas de ADMINISTRADOR ───────────────────────────────

        // GET /api/users — listar todos (FR-10).
        // POST no existe en /api/users (registro está en /api/users/register).
        before("/api/users", (req, res) -> {
            if ("GET".equalsIgnoreCase(req.requestMethod())) {
                verificarRol(req, res, RolUsuario.ADMINISTRADOR);
            }
        });

        before("/api/users/:id/desactivar", (req, res) ->
                verificarRol(req, res, RolUsuario.ADMINISTRADOR));

        before("/api/users/:id/activar", (req, res) ->
                verificarRol(req, res, RolUsuario.ADMINISTRADOR));

        before("/api/users/:id/rol", (req, res) ->
                verificarRol(req, res, RolUsuario.ADMINISTRADOR));

        // GET /api/disputes — todas las disputas (FR-10).
        // POST /api/disputes — abrir disputa (FR-09), permitido para cualquier usuario.
        // El método HTTP distingue los casos.
        before("/api/disputes", (req, res) -> {
            if ("GET".equalsIgnoreCase(req.requestMethod())) {
                verificarRol(req, res, RolUsuario.ADMINISTRADOR);
            }
        });

        before("/api/disputes/abiertas", (req, res) ->
                verificarRol(req, res, RolUsuario.ADMINISTRADOR));

        before("/api/disputes/:id/resolver", (req, res) ->
                verificarRol(req, res, RolUsuario.ADMINISTRADOR));

        // ─── Rutas exclusivas de REPARTIDOR ──────────────────────────────────

        before("/api/orders/:id/aceptar", (req, res) ->
                verificarRol(req, res, RolUsuario.REPARTIDOR));

        before("/api/orders/:id/iniciar", (req, res) ->
                verificarRol(req, res, RolUsuario.REPARTIDOR));

        before("/api/orders/:id/entregar", (req, res) ->
                verificarRol(req, res, RolUsuario.REPARTIDOR));
    }

    // ─── Lógica de verificación ───────────────────────────────────────────────

    /**
     * Verifica que el usuario autenticado tenga el rol requerido.
     *
     * Flujo:
     *  1. Ignora OPTIONS (CORS preflight, ya manejado por AuthFilter).
     *  2. Lee el uid del atributo establecido por AuthFilter.
     *  3. Busca el usuario en MongoDB.
     *  4. Comprueba que la cuenta esté activa.
     *  5. Comprueba que el rol coincida.
     *
     * @param req          Request de Spark
     * @param res          Response de Spark
     * @param rolRequerido Rol mínimo que debe tener el usuario
     */
    private void verificarRol(Request req, Response res, RolUsuario rolRequerido) {
        if ("OPTIONS".equalsIgnoreCase(req.requestMethod())) {
            return;
        }

        // El uid lo puso AuthFilter tras verificar el token JWT
        String uid = req.attribute(AuthFilter.ATTR_UID);
        if (uid == null) {
            // No debería ocurrir si AuthFilter está registrado primero
            halt(401, gson.toJson(Map.of("error", "No autenticado.")));
            return;
        }

        Optional<Usuario> usuarioOpt = userService.findById(uid);

        if (usuarioOpt.isEmpty()) {
            logger.warn("Token válido pero usuario sin perfil en MongoDB. uid={}", uid);
            halt(403, gson.toJson(Map.of(
                    "error", "Usuario no registrado en la plataforma.",
                    "detalle", "Completa el registro en POST /api/users/register."
            )));
            return;
        }

        Usuario usuario = usuarioOpt.get();

        if (!usuario.isActivo()) {
            logger.warn("Acceso denegado: cuenta desactivada. uid={}", uid);
            halt(403, gson.toJson(Map.of(
                    "error", "Cuenta desactivada.",
                    "detalle", "Contacta al administrador para reactivar tu cuenta."
            )));
            return;
        }

        if (usuario.getRol() != rolRequerido) {
            logger.warn("Acceso denegado: rol insuficiente. uid={} rolActual={} rolRequerido={}",
                    uid, usuario.getRol(), rolRequerido);
            halt(403, gson.toJson(Map.of(
                    "error", "Acceso denegado.",
                    "detalle", "Se requiere rol " + rolRequerido + ". Tu rol es: " + usuario.getRol() + "."
            )));
        }
    }
}
