package com.rappifavor.config;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class MongoConnectionTest {

    private static final Logger logger = LoggerFactory.getLogger(MongoConnectionTest.class);

    @BeforeAll
    static void setup() {
        AppConfig.init();
    }

    @Test
    void testConexionBasica() {
        MongoDatabase db = MongoConfig.getDatabase();
        assertNotNull(db);
        Document resultado = db.runCommand(new Document("ping", 1));
        assertNotNull(resultado);
        Number ok = (Number) resultado.get("ok");
        assertNotNull(ok);
        assertEquals(1, ok.intValue());
        logger.info("Conexion a MongoDB Atlas exitosa. Ping: {}", resultado.toJson());
    }

    @Test
    void testColeccionesAccesibles() {
        MongoDatabase db = MongoConfig.getDatabase();
        assertDoesNotThrow(() -> {
            db.getCollection(MongoConfig.COL_USUARIOS);
            db.getCollection(MongoConfig.COL_PEDIDOS);
            db.getCollection(MongoConfig.COL_DISPUTAS);
        });
        logger.info("Colecciones accesibles: {}, {}, {}",
            MongoConfig.COL_USUARIOS, MongoConfig.COL_PEDIDOS, MongoConfig.COL_DISPUTAS);
    }

    @Test
    void testEscrituraLectura() {
        MongoDatabase db = MongoConfig.getDatabase();
        var col = db.getCollection("test_conexion");
        Document doc = new Document("test", true).append("mensaje", "Rappi Favor test");
        assertDoesNotThrow(() -> col.insertOne(doc));
        Document encontrado = col.find(new Document("test", true)).first();
        assertNotNull(encontrado);
        assertEquals("Rappi Favor test", encontrado.getString("mensaje"));
        col.deleteMany(new Document("test", true));
        col.drop();
        logger.info("Escritura y lectura en MongoDB funcionan correctamente.");
    }
}
