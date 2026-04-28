package com.rappifavor;

import com.rappifavor.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static spark.Spark.*;

/**
 * Punto de entrada principal de la aplicación Rappi Favor.
 *
 * Responsabilidades:
 *  - Leer el puerto desde la variable de entorno PORT (Heroku lo inyecta)
 *  - Inicializar la configuración de la aplicación (MongoDB, Firebase)
 *  - Registrar las rutas del API REST
 *  - Configurar CORS para que el frontend pueda consumir el backend
 *
 * Arquitectura: Clean Architecture
 *   Controller → Service → Repository → MongoDB/Firebase
 */
public class RappiFavorApplication {

    private static final Logger logger = LoggerFactory.getLogger(RappiFavorApplication.class);

    public static void main(String[] args) {

        // ─── 1. Puerto ────────────────────────────────────────────────────
        // Heroku inyecta el puerto en la variable PORT.
        // Localmente (Podman) se usa el valor de .env o default 8080.
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        port(port);
        logger.info("Iniciando Rappi Favor Backend en puerto {}", port);

        // ─── 2. Configuración de la aplicación ───────────────────────────
        // Inicializa MongoDB y Firebase. Se delega a AppConfig.
        AppConfig.init();

        // ─── 3. CORS ──────────────────────────────────────────────────────
        // Permite solicitudes del frontend (cualquier origen en MVP académico).
        // En producción real se restringiría a dominios específicos.
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }
            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }
            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.type("application/json");
        });

        // ─── 4. Health check ──────────────────────────────────────────────
        // Endpoint mínimo para verificar que el servidor está vivo.
        // Útil para el cold start de Heroku y para CI.
        get("/health", (req, res) -> {
            res.status(200);
            return "{\"status\":\"UP\",\"service\":\"Rappi Favor Backend\",\"version\":\"1.0.0\"}";
        });

        // ─── 5. Rutas del API ─────────────────────────────────────────────
        // Se registrarán en pasos futuros (Fase 5: Controllers).
        // Por ahora se deja un placeholder comentado para referencia.
        //
        // Paso 5.1: UserController.register(app)
        // Paso 5.2: OrderController.register(app)
        // Paso 5.3: DisputeController.register(app)

        logger.info("Rappi Favor Backend iniciado correctamente en http://localhost:{}", port);
        logger.info("Health check disponible en: http://localhost:{}/health", port);
    }
}
