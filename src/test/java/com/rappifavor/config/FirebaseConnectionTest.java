package com.rappifavor.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FirebaseConnectionTest — Verifica que Firebase Admin SDK se inicializa correctamente.
 *
 * ⚠️ Este test requiere:
 *  - Archivo firebase-credentials.json en src/main/resources/
 *  - O variable FIREBASE_CREDENTIALS_JSON configurada
 *
 * Para correr solo este test:
 *   mvn test -Dtest=FirebaseConnectionTest
 */
class FirebaseConnectionTest {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConnectionTest.class);

    @BeforeAll
    static void setup() {
        AppConfig.init();
    }

    /**
     * Test 1: Verifica que FirebaseApp se inicializó correctamente.
     */
    @Test
    void testFirebaseAppInicializado() {
        logger.info("Test: Verificando inicialización de FirebaseApp...");

        assertFalse(FirebaseApp.getApps().isEmpty(),
            "FirebaseApp debe estar inicializado");

        FirebaseApp app = FirebaseConfig.getApp();
        assertNotNull(app, "FirebaseApp no debe ser null");

        logger.info("✅ FirebaseApp inicializado. Nombre: '{}'", app.getName());
    }

    /**
     * Test 2: Verifica que FirebaseAuth está disponible.
     * FirebaseAuth es el componente que usaremos para verificar tokens JWT.
     */
    @Test
    void testFirebaseAuthDisponible() {
        logger.info("Test: Verificando disponibilidad de FirebaseAuth...");

        FirebaseAuth auth = FirebaseAuth.getInstance();
        assertNotNull(auth, "FirebaseAuth no debe ser null");

        logger.info("✅ FirebaseAuth disponible y listo para verificar tokens JWT.");
    }

    /**
     * Test 3: Verifica que el proyecto de Firebase es accesible.
     * Intenta listar usuarios (lista vacía es válida — solo verifica conectividad).
     */
    @Test
    void testFirebaseConectividad() {
        logger.info("Test: Verificando conectividad con Firebase...");

        assertDoesNotThrow(() -> {
            // listUsers con pageSize=1 es la forma más ligera de verificar
            // que las credenciales son válidas y hay conexión con Firebase Auth
            FirebaseAuth.getInstance().listUsers(null, 1);
        }, "Firebase Auth debe ser accesible con las credenciales proporcionadas");

        logger.info("✅ Conectividad con Firebase Auth verificada correctamente.");
    }
}
