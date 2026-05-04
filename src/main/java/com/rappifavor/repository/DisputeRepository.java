package com.rappifavor.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.rappifavor.config.MongoConfig;
import com.rappifavor.model.Disputa;
import com.rappifavor.model.EstadoPedido;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DisputeRepository — Acceso a datos de la colección "disputas".
 *
 * Responsabilidades:
 *  - Generar y asignar el _id (ObjectId hex string) antes de insertar.
 *  - Persistir disputas nuevas y actualizar disputas resueltas.
 *  - Buscar disputas por id, pedido, usuario o estado.
 *
 * El id es un hex string de ObjectId, generado aquí en save().
 * La lógica de apertura y resolución de disputas vive en DisputeService (Fase 4).
 *
 * Requerimientos: FR-09 (abrir disputa), FR-10 (resolver disputa, admin).
 *
 * Capa: Repository — solo opera con MongoDB, sin lógica de negocio.
 * Dependencia: → config.MongoConfig (Paso 1.2)
 */
public class DisputeRepository {

    private static final Logger logger = LoggerFactory.getLogger(DisputeRepository.class);

    private final MongoCollection<Disputa> collection;

    /**
     * Obtiene la colección "disputas" tipada con el POJO codec.
     * Requiere que MongoConfig.init() haya sido llamado primero.
     */
    public DisputeRepository() {
        this.collection = MongoConfig.getDatabase()
                .getCollection(MongoConfig.COL_DISPUTAS, Disputa.class);
    }

    // ─── Escritura ────────────────────────────────────────────────────────────

    /**
     * Inserta una nueva disputa en la colección.
     * Genera un ObjectId y lo asigna como id (hex string) antes de insertar.
     * La disputa recibida es mutada: después de esta llamada, disputa.getId()
     * retorna el id asignado.
     *
     * @param disputa Entidad sin id (id = null al entrar)
     */
    public void save(Disputa disputa) {
        disputa.setId(new ObjectId().toHexString());
        collection.insertOne(disputa);
        logger.info("Disputa guardada. id={} pedidoId={}", disputa.getId(), disputa.getPedidoId());
    }

    /**
     * Reemplaza el documento completo de la disputa en la colección.
     * Úsalo para persistir la resolución del administrador:
     * resolucion, estado=RESUELTO y resoltoEn (FR-10).
     *
     * @param disputa Entidad con los datos actualizados (id obligatorio)
     * @return true si se modificó al menos un documento, false si el id no existe
     */
    public boolean update(Disputa disputa) {
        var result = collection.replaceOne(
                Filters.eq("_id", disputa.getId()),
                disputa
        );
        boolean modificado = result.getModifiedCount() > 0;
        if (modificado) {
            logger.info("Disputa actualizada. id={} estado={}", disputa.getId(), disputa.getEstado());
        } else {
            logger.warn("update() no encontró disputa con id={}", disputa.getId());
        }
        return modificado;
    }

    // ─── Lectura ──────────────────────────────────────────────────────────────

    /**
     * Busca una disputa por su id (ObjectId hex string).
     *
     * @param id Hex string del ObjectId (24 caracteres hexadecimales)
     * @return Optional con la disputa si existe, vacío si no
     */
    public Optional<Disputa> findById(String id) {
        Disputa disputa = collection.find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(disputa);
    }

    /**
     * Busca la disputa asociada a un pedido específico.
     * En el MVP, un pedido puede tener como máximo una disputa activa.
     * Usado para evitar disputas duplicadas sobre el mismo pedido (FR-09).
     *
     * @param pedidoId Id del pedido disputado
     * @return Optional con la disputa si existe, vacío si ese pedido no tiene disputa
     */
    public Optional<Disputa> findByPedidoId(String pedidoId) {
        Disputa disputa = collection.find(Filters.eq("pedidoId", pedidoId)).first();
        return Optional.ofNullable(disputa);
    }

    /**
     * Retorna todas las disputas abiertas por un usuario.
     * Permite al usuario ver el estado de sus controversias (FR-09).
     *
     * @param usuarioId Firebase UID del usuario
     * @return Lista de disputas del usuario, vacía si no tiene ninguna
     */
    public List<Disputa> findByUsuarioId(String usuarioId) {
        List<Disputa> disputas = new ArrayList<>();
        collection.find(Filters.eq("usuarioId", usuarioId)).into(disputas);
        return disputas;
    }

    /**
     * Retorna todas las disputas en un estado específico.
     * Caso de uso principal: listar disputas DISPUTADO (abiertas) para
     * que el administrador las atienda (FR-10).
     *
     * El POJO codec almacena el enum como String (nombre del enum),
     * por eso se filtra con estado.name() y no con el objeto enum directamente.
     *
     * @param estado Estado a filtrar (ej. EstadoPedido.DISPUTADO o EstadoPedido.RESUELTO)
     * @return Lista de disputas en ese estado, vacía si no hay ninguna
     */
    public List<Disputa> findByEstado(EstadoPedido estado) {
        List<Disputa> disputas = new ArrayList<>();
        collection.find(Filters.eq("estado", estado.name())).into(disputas);
        return disputas;
    }

    /**
     * Retorna todas las disputas de la plataforma.
     * Usado por el administrador para supervisión general (FR-10).
     *
     * @return Lista de todas las disputas, vacía si no hay ninguna
     */
    public List<Disputa> findAll() {
        List<Disputa> disputas = new ArrayList<>();
        collection.find().into(disputas);
        return disputas;
    }
}
