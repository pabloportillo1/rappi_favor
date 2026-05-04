package com.rappifavor.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.rappifavor.config.MongoConfig;
import com.rappifavor.model.RolUsuario;
import com.rappifavor.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * UserRepository — Acceso a datos de la colección "usuarios".
 *
 * Responsabilidades:
 *  - Persistir nuevos usuarios (Firebase UID como _id).
 *  - Buscar usuarios por id, email o rol.
 *  - Actualizar y eliminar usuarios.
 *
 * El id del Usuario es el UID de Firebase Auth; el llamador (UserService)
 * lo asigna antes de invocar save(). Este repositorio no genera ids.
 *
 * Requerimientos: FR-01 (registro), FR-02 (auth), FR-10 (admin).
 *
 * Capa: Repository — solo opera con MongoDB, sin lógica de negocio.
 * Dependencia: → config.MongoConfig (Paso 1.2)
 */
public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    private final MongoCollection<Usuario> collection;

    /**
     * Obtiene la colección "usuarios" tipada con el POJO codec.
     * Requiere que MongoConfig.init() haya sido llamado primero.
     */
    public UserRepository() {
        this.collection = MongoConfig.getDatabase()
                .getCollection(MongoConfig.COL_USUARIOS, Usuario.class);
    }

    // ─── Escritura ────────────────────────────────────────────────────────────

    /**
     * Inserta un nuevo usuario en la colección.
     * El campo id debe ser el Firebase UID, asignado previamente por UserService.
     *
     * @param usuario Entidad con id (Firebase UID) ya asignado
     * @throws com.mongodb.MongoWriteException si el id ya existe (clave duplicada)
     */
    public void save(Usuario usuario) {
        collection.insertOne(usuario);
        logger.info("Usuario guardado. id={}", usuario.getId());
    }

    /**
     * Reemplaza el documento completo del usuario en la colección.
     * Úsalo para actualizar nombre, rol, estado activo, etc.
     *
     * @param usuario Entidad con los datos actualizados
     * @return true si se modificó al menos un documento, false si el id no existe
     */
    public boolean update(Usuario usuario) {
        var result = collection.replaceOne(
                Filters.eq("_id", usuario.getId()),
                usuario
        );
        boolean modificado = result.getModifiedCount() > 0;
        if (modificado) {
            logger.info("Usuario actualizado. id={}", usuario.getId());
        } else {
            logger.warn("update() no encontró usuario con id={}", usuario.getId());
        }
        return modificado;
    }

    // ─── Lectura ──────────────────────────────────────────────────────────────

    /**
     * Busca un usuario por su Firebase UID (_id en MongoDB).
     *
     * @param id Firebase UID del usuario
     * @return Optional con el usuario si existe, vacío si no
     */
    public Optional<Usuario> findById(String id) {
        Usuario usuario = collection.find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(usuario);
    }

    /**
     * Busca un usuario por su correo electrónico.
     * Usado en el flujo de registro para verificar unicidad del email (FR-01).
     *
     * @param email Correo a buscar (ej. usuario@iteso.mx)
     * @return Optional con el usuario si existe, vacío si no
     */
    public Optional<Usuario> findByEmail(String email) {
        Usuario usuario = collection.find(Filters.eq("email", email)).first();
        return Optional.ofNullable(usuario);
    }

    /**
     * Retorna todos los usuarios de la plataforma.
     * Usado por el administrador (FR-10).
     *
     * @return Lista de usuarios, vacía si no hay ninguno
     */
    public List<Usuario> findAll() {
        List<Usuario> usuarios = new ArrayList<>();
        collection.find().into(usuarios);
        return usuarios;
    }

    /**
     * Retorna todos los usuarios con un rol específico.
     * Útil para listar repartidores disponibles o usuarios con disputas.
     *
     * El POJO codec almacena el enum como String (nombre del enum),
     * por eso se filtra con rol.name() y no con el objeto enum directamente.
     *
     * @param rol Rol a filtrar (USUARIO, REPARTIDOR, ADMINISTRADOR)
     * @return Lista de usuarios con ese rol, vacía si no hay ninguno
     */
    public List<Usuario> findByRol(RolUsuario rol) {
        List<Usuario> usuarios = new ArrayList<>();
        collection.find(Filters.eq("rol", rol.name())).into(usuarios);
        return usuarios;
    }
}
