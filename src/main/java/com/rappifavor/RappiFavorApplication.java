package com.rappifavor;

import com.rappifavor.config.AppConfig;
import com.rappifavor.config.AuthFilter;
import com.rappifavor.config.RoleFilter;
import com.rappifavor.controller.ChatController;
import com.rappifavor.controller.DisputeController;
import com.rappifavor.controller.OrderController;
import com.rappifavor.controller.UserController;
import com.rappifavor.repository.DisputeRepository;
import com.rappifavor.repository.OrderRepository;
import com.rappifavor.repository.UserRepository;
import com.rappifavor.service.ChatService;
import com.rappifavor.service.DisputeService;
import com.rappifavor.service.OrderService;
import com.rappifavor.service.UserService;
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

        // ─── 5. Grafo de dependencias: Repository → Service ──────────────
        UserRepository    userRepo    = new UserRepository();
        OrderRepository   orderRepo   = new OrderRepository();
        DisputeRepository disputeRepo = new DisputeRepository();

        UserService    userService    = new UserService(userRepo);
        OrderService   orderService   = new OrderService(orderRepo);
        DisputeService disputeService = new DisputeService(disputeRepo, orderService);
        ChatService    chatService    = new ChatService(orderService);

        // ─── 6. Filtros de seguridad (deben ir ANTES que los controllers) ─
        // Paso 6.1: Verificación del token JWT de Firebase en todas las rutas /api/*
        new AuthFilter().apply();

        // Paso 6.2: Control de acceso por rol (ADMINISTRADOR, REPARTIDOR)
        new RoleFilter(userService).apply();

        // ─── 7. Rutas del API ─────────────────────────────────────────────
        // Paso 5.1: Endpoints de usuarios
        new UserController(userService).register();

        // Paso 5.2: Endpoints de pedidos
        new OrderController(orderService).register();

        // Paso 5.3: Endpoints de disputas
        new DisputeController(disputeService).register();

        // Paso 7.2: Endpoint de chat
        new ChatController(chatService).register();

        logger.info("Rappi Favor Backend iniciado correctamente en http://localhost:{}", port);
        logger.info("Health check disponible en: http://localhost:{}/health", port);
    }
}
