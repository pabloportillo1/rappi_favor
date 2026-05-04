package com.rappifavor.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static spark.Spark.before;
import static spark.Spark.halt;

/**
 * AuthFilter — Filtro de autenticación JWT para todas las rutas /api/*.
 *
 * Responsabilidades:
 *  - Extraer el token Bearer del header Authorization.
 *  - Verificar el token con Firebase Auth (firma, expiración, audiencia).
 *  - Almacenar el Firebase UID en el atributo ATTR_UID del request
 *    para que los controllers y RoleFilter lo consuman sin re-verificar.
 *
 * Comportamiento por tipo de request:
 *  - OPTIONS (preflight CORS) → se permite sin verificación.
 *  - Sin header Authorization → 401 Unauthorized.
 *  - Token inválido o expirado → 401 Unauthorized.
 *  - Token válido → atributo "uid" disponible en el request.
 *
 * Rutas excluidas de autenticación:
 *  - GET /health  (health check, no tiene prefijo /api/)
 *
 * Dependencia: → config.FirebaseConfig (Paso 1.3) — FirebaseApp debe estar
 *               inicializado antes de llamar a apply().
 *
 * Referencia: FR-02 (autenticación), Paso 6.1.
 */
public class AuthFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);
    private static final Gson gson = new Gson();

    /** Clave del atributo de request donde se almacena el Firebase UID verificado. */
    public static final String ATTR_UID = "uid";

    /**
     * Registra el filtro de autenticación en Spark.
     * Debe llamarse DESPUÉS de AppConfig.init() y ANTES de registrar controllers.
     *
     * El filtro aplica a todas las rutas bajo /api/* usando el mecanismo
     * before() de Spark, que ejecuta los filtros en orden de registro.
     */
    public void apply() {
        before("/api/*", (req, res) -> {

            // Peticiones OPTIONS son CORS preflight — no requieren token
            if ("OPTIONS".equalsIgnoreCase(req.requestMethod())) {
                return;
            }

            // ─── 1. Extraer el header Authorization ──────────────────────
            String authHeader = req.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("Request sin token. método={} path={}", req.requestMethod(), req.pathInfo());
                halt(401, gson.toJson(Map.of(
                        "error", "Token de autorización requerido.",
                        "detalle", "Incluye el header: Authorization: Bearer <firebase-id-token>"
                )));
                return;
            }

            // ─── 2. Verificar el token con Firebase Auth ──────────────────
            String token = authHeader.substring(7).trim();
            try {
                FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);

                // Almacena el UID en el request para que lo usen controllers y RoleFilter
                req.attribute(ATTR_UID, decoded.getUid());

                logger.debug("Token verificado. uid={} path={}", decoded.getUid(), req.pathInfo());

            } catch (FirebaseAuthException e) {
                logger.warn("Token inválido. causa={} path={}", e.getMessage(), req.pathInfo());
                halt(401, gson.toJson(Map.of(
                        "error", "Token inválido o expirado.",
                        "detalle", "Obtén un token fresco con Firebase Auth y reintenta."
                )));
            }
        });
    }
}
