package com.rappifavor.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.rappifavor.config.MongoConfig;
import com.rappifavor.model.EstadoPedido;
import com.rappifavor.model.Pedido;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OrderRepository — Acceso a datos de la colección "pedidos".
 *
 * Responsabilidades:
 *  - Generar y asignar el _id (ObjectId hex string) antes de insertar.
 *  - Persistir pedidos nuevos y actualizar pedidos existentes.
 *  - Buscar pedidos por id, usuario, repartidor o estado del AFD.
 *
 * El id es un hex string de ObjectId (ej. "663f1a2b4c5d6e7f8a9b0c1d"),
 * generado aquí en save() antes de llamar a insertOne().
 * El estado y las transiciones del AFD los gestiona OrderService (Fase 4).
 *
 * Requerimientos: FR-03 (crear), FR-04 (listar), FR-05 (aceptar),
 *                 FR-06 (historial), FR-07 (en camino), FR-08 (entregar),
 *                 FR-11 (admin).
 *
 * Capa: Repository — solo opera con MongoDB, sin lógica de negocio.
 * Dependencia: → config.MongoConfig (Paso 1.2)
 */
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);

    private final MongoCollection<Pedido> collection;

    /**
     * Obtiene la colección "pedidos" tipada con el POJO codec.
     * Requiere que MongoConfig.init() haya sido llamado primero.
     */
    public OrderRepository() {
        this.collection = MongoConfig.getDatabase()
                .getCollection(MongoConfig.COL_PEDIDOS, Pedido.class);
    }

    // ─── Escritura ────────────────────────────────────────────────────────────

    /**
     * Inserta un nuevo pedido en la colección.
     * Genera un ObjectId y lo asigna como id (hex string) antes de insertar.
     * El pedido recibido es mutado: después de esta llamada, pedido.getId()
     * retorna el id asignado.
     *
     * @param pedido Entidad sin id (id = null al entrar)
     */
    public void save(Pedido pedido) {
        pedido.setId(new ObjectId().toHexString());
        collection.insertOne(pedido);
        logger.info("Pedido guardado. id={}", pedido.getId());
    }

    /**
     * Reemplaza el documento completo del pedido en la colección.
     * Úsalo para persisitir transiciones de estado del AFD y cambios
     * en repartidorId, historial, actualizadoEn, etc.
     *
     * @param pedido Entidad con los datos actualizados (id obligatorio)
     * @return true si se modificó al menos un documento, false si el id no existe
     */
    public boolean update(Pedido pedido) {
        var result = collection.replaceOne(
                Filters.eq("_id", pedido.getId()),
                pedido
        );
        boolean modificado = result.getModifiedCount() > 0;
        if (modificado) {
            logger.info("Pedido actualizado. id={} estado={}", pedido.getId(), pedido.getEstado());
        } else {
            logger.warn("update() no encontró pedido con id={}", pedido.getId());
        }
        return modificado;
    }

    // ─── Lectura ──────────────────────────────────────────────────────────────

    /**
     * Busca un pedido por su id (ObjectId hex string).
     *
     * @param id Hex string del ObjectId (24 caracteres hexadecimales)
     * @return Optional con el pedido si existe, vacío si no
     */
    public Optional<Pedido> findById(String id) {
        Pedido pedido = collection.find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(pedido);
    }

    /**
     * Retorna todos los pedidos creados por un usuario (FR-04).
     *
     * @param usuarioId Firebase UID del usuario solicitante
     * @return Lista de pedidos del usuario, vacía si no tiene ninguno
     */
    public List<Pedido> findByUsuarioId(String usuarioId) {
        List<Pedido> pedidos = new ArrayList<>();
        collection.find(Filters.eq("usuarioId", usuarioId)).into(pedidos);
        return pedidos;
    }

    /**
     * Retorna todos los pedidos asignados a un repartidor.
     * Usado para mostrar el historial de trabajo del repartidor.
     *
     * @param repartidorId Firebase UID del repartidor
     * @return Lista de pedidos del repartidor, vacía si no tiene ninguno
     */
    public List<Pedido> findByRepartidorId(String repartidorId) {
        List<Pedido> pedidos = new ArrayList<>();
        collection.find(Filters.eq("repartidorId", repartidorId)).into(pedidos);
        return pedidos;
    }

    /**
     * Retorna todos los pedidos en un estado específico del AFD.
     * Caso de uso principal: listar pedidos PENDIENTE para que
     * los repartidores puedan aceptarlos (FR-05).
     *
     * El POJO codec almacena el enum como String (nombre del enum),
     * por eso se filtra con estado.name() y no con el objeto enum directamente.
     *
     * @param estado Estado del AFD a filtrar (ej. EstadoPedido.PENDIENTE)
     * @return Lista de pedidos en ese estado, vacía si no hay ninguno
     */
    public List<Pedido> findByEstado(EstadoPedido estado) {
        List<Pedido> pedidos = new ArrayList<>();
        collection.find(Filters.eq("estado", estado.name())).into(pedidos);
        return pedidos;
    }

    /**
     * Retorna todos los pedidos de la plataforma.
     * Usado por el administrador para supervisión general (FR-11).
     *
     * @return Lista de todos los pedidos, vacía si no hay ninguno
     */
    public List<Pedido> findAll() {
        List<Pedido> pedidos = new ArrayList<>();
        collection.find().into(pedidos);
        return pedidos;
    }
}
