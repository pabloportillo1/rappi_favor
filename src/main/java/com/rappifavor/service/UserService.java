package com.rappifavor.service;

import com.rappifavor.model.RolUsuario;
import com.rappifavor.model.Usuario;
import com.rappifavor.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * UserService — Lógica de negocio para la gestión de usuarios.
 *
 * Responsabilidades:
 *  - Validar que el correo sea institucional @iteso.mx (FR-01).
 *  - Verificar unicidad del correo antes de registrar (FR-01).
 *  - Asignar timestamps de creación.
 *  - Activar y desactivar cuentas (FR-10, admin).
 *
 * Esta capa es la única con reglas de negocio sobre usuarios.
 * El repositorio solo persiste; los controladores solo enrutan.
 *
 * Requerimientos: FR-01 (registro), FR-02 (auth), FR-10 (admin).
 *
 * Dependencia: → repository.UserRepository (Paso 3.1)
 */
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    /** Patrón de correo institucional ITESO (SRS Sección 3.1, RNF-07). */
    private static final Pattern EMAIL_ITESO =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@iteso\\.mx$");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ─── Comandos ─────────────────────────────────────────────────────────────

    /**
     * Registra un nuevo usuario en la plataforma (FR-01).
     *
     * Validaciones:
     *  1. El correo debe ser @iteso.mx.
     *  2. El correo no debe estar ya registrado.
     *  3. El Firebase UID no debe estar ya registrado.
     *
     * El id (Firebase UID) lo obtiene el controller de FirebaseAuth
     * y lo pasa aquí; este servicio no interactúa con Firebase directamente.
     *
     * @param firebaseUid UID emitido por Firebase Auth (será el _id en MongoDB)
     * @param nombre      Nombre completo del usuario
     * @param email       Correo institucional (@iteso.mx)
     * @param rol         Rol asignado al registrarse
     * @return Usuario persistido con timestamps asignados
     * @throws IllegalArgumentException si el email no es válido o ya está registrado
     */
    public Usuario registrar(String firebaseUid, String nombre, String email, RolUsuario rol) {
        if (!EMAIL_ITESO.matcher(email).matches()) {
            throw new IllegalArgumentException(
                    "El correo debe ser institucional @iteso.mx. Recibido: " + email);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException(
                    "El correo ya está registrado: " + email);
        }
        if (userRepository.findById(firebaseUid).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un usuario con este id: " + firebaseUid);
        }

        Usuario usuario = new Usuario(firebaseUid, nombre, email, rol);
        usuario.setCreadoEn(now());
        userRepository.save(usuario);

        logger.info("Usuario registrado. id={} email={} rol={}", firebaseUid, email, rol);
        return usuario;
    }

    /**
     * Desactiva la cuenta de un usuario (FR-10, solo administrador).
     * El usuario desactivado no puede iniciar sesión ni operar en la plataforma.
     *
     * @param id Firebase UID del usuario a desactivar
     * @throws IllegalArgumentException si el usuario no existe
     */
    public void desactivar(String id) {
        Usuario usuario = findByIdOrThrow(id);
        usuario.setActivo(false);
        userRepository.update(usuario);
        logger.info("Usuario desactivado. id={}", id);
    }

    /**
     * Reactiva la cuenta de un usuario (FR-10, solo administrador).
     *
     * @param id Firebase UID del usuario a activar
     * @throws IllegalArgumentException si el usuario no existe
     */
    public void activar(String id) {
        Usuario usuario = findByIdOrThrow(id);
        usuario.setActivo(true);
        userRepository.update(usuario);
        logger.info("Usuario activado. id={}", id);
    }

    /**
     * Cambia el rol de un usuario (FR-10, solo administrador).
     *
     * @param id  Firebase UID del usuario
     * @param rol Nuevo rol a asignar
     * @throws IllegalArgumentException si el usuario no existe
     */
    public void cambiarRol(String id, RolUsuario rol) {
        Usuario usuario = findByIdOrThrow(id);
        usuario.setRol(rol);
        userRepository.update(usuario);
        logger.info("Rol cambiado. id={} nuevoRol={}", id, rol);
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    /**
     * Busca un usuario por su Firebase UID.
     *
     * @param id Firebase UID
     * @return Optional con el usuario si existe, vacío si no
     */
    public Optional<Usuario> findById(String id) {
        return userRepository.findById(id);
    }

    /**
     * Busca un usuario por su correo electrónico.
     *
     * @param email Correo a buscar
     * @return Optional con el usuario si existe, vacío si no
     */
    public Optional<Usuario> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Retorna todos los usuarios de la plataforma (FR-10, admin).
     *
     * @return Lista de todos los usuarios, vacía si no hay ninguno
     */
    public List<Usuario> findAll() {
        return userRepository.findAll();
    }

    /**
     * Retorna todos los usuarios con un rol específico.
     * Útil para listar repartidores disponibles o administradores.
     *
     * @param rol Rol a filtrar
     * @return Lista de usuarios con ese rol, vacía si no hay ninguno
     */
    public List<Usuario> findByRol(RolUsuario rol) {
        return userRepository.findByRol(rol);
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private Usuario findByIdOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
    }

    private String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
