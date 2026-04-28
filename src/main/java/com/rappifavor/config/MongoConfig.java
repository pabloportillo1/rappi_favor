package com.rappifavor.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;

/**
 * MongoConfig — Gestiona la conexión a MongoDB Atlas.
 *
 * Responsabilidades:
 *  - Crear y mantener una única instancia de MongoClient (Singleton)
 *  - Proveer acceso a la base de datos y colecciones
 *  - Configurar el codec POJO para mapeo automático Java ↔ BSON
 *
 * Patrón: Singleton — una sola conexión compartida por toda la aplicación.
 * MongoDB Driver gestiona internamente un pool de conexiones TCP/TLS.
 *
 * Protocolo: TCP/TLS 27017 — MongoDB Wire Protocol (cifrado en tránsito).
 */
public class MongoConfig {

    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);

    // ─── Singleton ────────────────────────────────────────────────────────────
    private static MongoClient mongoClient;
    private static MongoDatabase database;

    // Nombres de colecciones — consistentes con el modelo de datos del SRS
    public static final String COL_USUARIOS  = "usuarios";
    public static final String COL_PEDIDOS   = "pedidos";
    public static final String COL_DISPUTAS  = "disputas";

    /**
     * Inicializa la conexión a MongoDB Atlas.
     * Llamado desde AppConfig.init() al arrancar la aplicación.
     *
     * @throws IllegalStateException si MONGO_URI no está configurada
     */
    public static void init() {
        logger.info("Inicializando conexión a MongoDB Atlas...");

        // Lee la URI y nombre de BD desde variables de entorno
        String mongoUri    = AppConfig.getEnv("MONGO_URI");
        String dbName      = AppConfig.getEnv("MONGO_DB_NAME", "rappifavor");

        // ─── Codec Registry para mapeo POJO ──────────────────────────────
        // Permite usar clases Java directamente con el driver MongoDB
        // sin necesidad de convertir manualmente a/desde Document.
        CodecRegistry pojoCodecRegistry = fromRegistries(
            getDefaultCodecRegistry(),
            fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );

        // ─── Configuración del cliente ────────────────────────────────────
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(mongoUri))
            .codecRegistry(pojoCodecRegistry)
            .build();

        // ─── Crear cliente (pool de conexiones TCP/TLS) ───────────────────
        mongoClient = MongoClients.create(settings);
        database    = mongoClient.getDatabase(dbName).withCodecRegistry(pojoCodecRegistry);

        logger.info("Conexión a MongoDB Atlas establecida. Base de datos: '{}'", dbName);
    }

    /**
     * Retorna la instancia de MongoDatabase.
     * Usado por los repositorios para obtener colecciones.
     *
     * @return MongoDatabase instancia de la BD
     * @throws IllegalStateException si init() no fue llamado primero
     */
    public static MongoDatabase getDatabase() {
        if (database == null) {
            throw new IllegalStateException(
                "MongoConfig no inicializado. Llama a MongoConfig.init() primero."
            );
        }
        return database;
    }

    /**
     * Retorna el MongoClient.
     * Útil para tests y operaciones administrativas.
     */
    public static MongoClient getClient() {
        return mongoClient;
    }

    /**
     * Cierra la conexión a MongoDB.
     * Llamado al apagar la aplicación para liberar recursos.
     */
    public static void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("Conexión a MongoDB cerrada.");
        }
    }
}
