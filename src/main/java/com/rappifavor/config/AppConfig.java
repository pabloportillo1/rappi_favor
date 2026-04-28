package com.rappifavor.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AppConfig — Orquestador central de configuración.
 *
 * Se encarga de:
 *  1. Cargar variables de entorno desde .env (local) o Config Vars (Heroku)
 *  2. Inicializar MongoDB (Paso 1.2)
 *  3. Inicializar Firebase Admin SDK (Paso 1.3)
 *
 * Patrón: Configuration class con inicialización estática.
 * La instancia de Dotenv se comparte para evitar múltiples lecturas del archivo.
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    // Instancia compartida de Dotenv para leer variables de entorno
    private static Dotenv dotenv;

    /**
     * Inicializa todos los servicios de configuración.
     * Llamado una única vez desde RappiFavorApplication.main()
     */
    public static void init() {
        logger.info("Inicializando configuración de la aplicación...");

        // Carga variables de entorno
        loadEnv();

        // MongoDB se inicializa en Paso 1.2
        // MongoConfig.init(dotenv);

        // Firebase se inicializa en Paso 1.3
        // FirebaseConfig.init(dotenv);

        logger.info("Configuración inicializada correctamente.");
    }

    /**
     * Carga variables de entorno.
     *
     * Comportamiento:
     *  - Si existe .env en el directorio raíz (local/Podman): lo lee.
     *  - Si no existe (Heroku): usa System.getenv() directamente.
     *  - Nunca falla si el archivo no existe (ignoreIfMissing).
     */
    private static void loadEnv() {
        dotenv = Dotenv.configure()
                .ignoreIfMissing()   // No falla si .env no existe (Heroku)
                .load();
        logger.info("Variables de entorno cargadas.");
    }

    /**
     * Obtiene el valor de una variable de entorno.
     * Primero busca en .env (local), luego en variables del sistema (Heroku).
     *
     * @param key Nombre de la variable
     * @return Valor de la variable
     * @throws IllegalStateException si la variable no existe
     */
    public static String getEnv(String key) {
        String value = dotenv.get(key, System.getenv(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Variable de entorno requerida no encontrada: " + key +
                ". Agrégala en .env (local) o Config Vars (Heroku)."
            );
        }
        return value;
    }

    /**
     * Obtiene el valor de una variable de entorno con valor por defecto.
     *
     * @param key          Nombre de la variable
     * @param defaultValue Valor por defecto si la variable no existe
     * @return Valor de la variable o el valor por defecto
     */
    public static String getEnv(String key, String defaultValue) {
        String value = dotenv.get(key, System.getenv(key));
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
