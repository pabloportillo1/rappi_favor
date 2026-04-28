package com.rappifavor.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * FirebaseConfig — Inicializa el Firebase Admin SDK.
 *
 * Responsabilidades:
 *  - Cargar las credenciales del archivo JSON o variable de entorno
 *  - Inicializar FirebaseApp (singleton de Firebase)
 *  - Proveer acceso al FirebaseApp inicializado
 *
 * Usos en el sistema:
 *  - Paso 6.1: Verificar tokens JWT en el AuthFilter
 *  - Paso 7.1: Acceder a Firebase Realtime DB para el chat
 *
 * Modos de credenciales (en orden de prioridad):
 *  1. Variable de entorno FIREBASE_CREDENTIALS_JSON (Heroku — JSON completo como string)
 *  2. Archivo en ruta FIREBASE_CREDENTIALS_PATH (local/Podman — ruta al .json)
 */
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    // URL de la Realtime Database (formato: https://<project-id>-default-rtdb.firebaseio.com)
    private static final String RTDB_URL_TEMPLATE = "https://%s-default-rtdb.firebaseio.com";

    /**
     * Inicializa Firebase Admin SDK.
     * Llamado desde AppConfig.init() al arrancar la aplicación.
     *
     * @throws RuntimeException si las credenciales no se pueden cargar
     */
    public static void init() {
        logger.info("Inicializando Firebase Admin SDK...");

        // Evita inicializar dos veces (por ejemplo en tests)
        if (!FirebaseApp.getApps().isEmpty()) {
            logger.info("Firebase Admin SDK ya estaba inicializado.");
            return;
        }

        try {
            GoogleCredentials credentials = loadCredentials();
            String projectId = extractProjectId(credentials);
            String rtdbUrl = String.format(RTDB_URL_TEMPLATE, projectId);

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setDatabaseUrl(rtdbUrl)
                .build();

            FirebaseApp.initializeApp(options);
            logger.info("Firebase Admin SDK inicializado. Proyecto: '{}', RTDB: '{}'",
                projectId, rtdbUrl);

        } catch (IOException e) {
            throw new RuntimeException(
                "Error al inicializar Firebase Admin SDK: " + e.getMessage() +
                "\nVerifica que el archivo de credenciales existe en la ruta configurada.", e
            );
        }
    }

    /**
     * Carga las credenciales de Firebase.
     *
     * Prioridad:
     *  1. Variable FIREBASE_CREDENTIALS_JSON (Heroku — JSON como string en variable de entorno)
     *  2. Variable FIREBASE_CREDENTIALS_PATH (local — ruta al archivo .json)
     *
     * @return GoogleCredentials listas para usar
     * @throws IOException si no se pueden cargar las credenciales
     */
    private static GoogleCredentials loadCredentials() throws IOException {

        // ─── Modo 1: JSON completo en variable de entorno (Heroku) ────────
        String credentialsJson = System.getenv("FIREBASE_CREDENTIALS_JSON");
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            logger.info("Cargando credenciales Firebase desde variable de entorno JSON.");
            InputStream stream = new java.io.ByteArrayInputStream(
                credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            return GoogleCredentials.fromStream(stream);
        }

        // ─── Modo 2: Ruta al archivo .json (local/Podman) ─────────────────
        String credentialsPath = AppConfig.getEnv(
            "FIREBASE_CREDENTIALS_PATH",
            "src/main/resources/firebase-credentials.json"
        );

        logger.info("Cargando credenciales Firebase desde archivo: '{}'", credentialsPath);

        try (FileInputStream fis = new FileInputStream(credentialsPath)) {
            return GoogleCredentials.fromStream(fis);
        } catch (IOException e) {
            throw new IOException(
                "No se encontró el archivo de credenciales Firebase en: " + credentialsPath +
                "\n→ Local: coloca el archivo JSON en src/main/resources/firebase-credentials.json" +
                "\n→ Heroku: configura la variable FIREBASE_CREDENTIALS_JSON con el contenido del JSON",
                e
            );
        }
    }

    /**
     * Extrae el project ID de las credenciales para construir la URL de RTDB.
     * Si no se puede extraer, usa el valor de la variable FIREBASE_PROJECT_ID.
     */
    private static String extractProjectId(GoogleCredentials credentials) {
        // Intenta obtener el project ID desde las credenciales de cuenta de servicio
        if (credentials instanceof com.google.auth.oauth2.ServiceAccountCredentials) {
            String projectId = ((com.google.auth.oauth2.ServiceAccountCredentials) credentials)
                .getProjectId();
            if (projectId != null && !projectId.isBlank()) {
                return projectId;
            }
        }
        // Fallback: variable de entorno
        return AppConfig.getEnv("FIREBASE_PROJECT_ID", "rappifavor");
    }

    /**
     * Retorna el FirebaseApp inicializado.
     * @throws IllegalStateException si init() no fue llamado primero
     */
    public static FirebaseApp getApp() {
        if (FirebaseApp.getApps().isEmpty()) {
            throw new IllegalStateException(
                "FirebaseConfig no inicializado. Llama a FirebaseConfig.init() primero."
            );
        }
        return FirebaseApp.getInstance();
    }
}
