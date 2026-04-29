package com.rappifavor.model;

import org.bson.codecs.pojo.annotations.BsonId;

/**
 * Entidad Usuario — colección "usuarios" en MongoDB.
 *
 * El id corresponde al UID de Firebase Auth, lo que vincula
 * el perfil en MongoDB con las credenciales en Firebase sin
 * duplicar información de autenticación.
 *
 * La contraseña NO se almacena aquí: Firebase Auth la gestiona.
 *
 * Requerimientos: FR-01 (registro), FR-02 (auth), FR-10 (admin).
 * Validación de dominio @iteso.mx: responsabilidad del UserService (Fase 4).
 *
 * Compatibilidad:
 *  - MongoDB POJO codec: constructor sin argumentos + getters/setters requeridos.
 *  - Gson: serializa todos los campos públicamente accesibles.
 */
public class Usuario {

    /**
     * Firebase UID — actúa como _id en MongoDB.
     * El UserRepository lo recibe de FirebaseAuth y lo asigna antes de insertar.
     */
    @BsonId
    private String id;

    /** Nombre completo del usuario. */
    private String nombre;

    /**
     * Correo institucional. Debe cumplir: ^[a-zA-Z0-9._%+-]+@iteso\.mx$
     * La validación se realiza en UserService, no aquí (modelo no valida).
     */
    private String email;

    /** Rol asignado al usuario en la plataforma. */
    private RolUsuario rol;

    /**
     * Indica si la cuenta está activa.
     * El administrador puede desactivarla (FR-10).
     */
    private boolean activo;

    /** Fecha y hora de creación de la cuenta en formato ISO 8601. */
    private String creadoEn;

    // ─── Constructor requerido por el POJO codec de MongoDB ──────────────────
    public Usuario() {}

    public Usuario(String id, String nombre, String email, RolUsuario rol) {
        this.id       = id;
        this.nombre   = nombre;
        this.email    = email;
        this.rol      = rol;
        this.activo   = true;
    }

    // ─── Getters y Setters ────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public RolUsuario getRol() {
        return rol;
    }

    public void setRol(RolUsuario rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(String creadoEn) {
        this.creadoEn = creadoEn;
    }
}
