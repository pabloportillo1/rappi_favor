package com.rappifavor.model;

import org.bson.codecs.pojo.annotations.BsonId;

/**
 * Entidad Disputa — colección "disputas" en MongoDB.
 *
 * Se crea cuando un usuario abre una controversia sobre un pedido
 * entregado (FR-09). El administrador la resuelve (FR-10),
 * transicionando el estado del pedido a RESUELTO (q7 del AFD).
 *
 * Requerimientos: FR-09, FR-10.
 *
 * Compatibilidad:
 *  - MongoDB POJO codec: constructor sin argumentos + getters/setters requeridos.
 *  - Gson: serializa los campos directamente.
 */
public class Disputa {

    /**
     * Identificador único. Generado por el DisputaRepository como hex string
     * de ObjectId antes de insertar.
     */
    @BsonId
    private String id;

    /** ID del pedido sobre el que se abre la disputa. */
    private String pedidoId;

    /** Firebase UID del usuario que abrió la disputa. */
    private String usuarioId;

    /** Descripción del motivo de la disputa ingresada por el usuario. */
    private String motivo;

    /**
     * Estado de la disputa.
     * DISPUTADO al crearse; RESUELTO cuando el administrador la cierra.
     */
    private EstadoPedido estado;

    /**
     * Descripción de la resolución emitida por el administrador.
     * Es null hasta que el administrador resuelve la disputa (FR-10).
     */
    private String resolucion;

    /** Fecha y hora de apertura de la disputa en formato ISO 8601. */
    private String creadoEn;

    /**
     * Fecha y hora de resolución en formato ISO 8601.
     * Es null hasta que el administrador cierra la disputa.
     */
    private String resoltoEn;

    // ─── Constructor requerido por el POJO codec de MongoDB ──────────────────
    public Disputa() {}

    public Disputa(String pedidoId, String usuarioId, String motivo) {
        this.pedidoId  = pedidoId;
        this.usuarioId = usuarioId;
        this.motivo    = motivo;
        this.estado    = EstadoPedido.DISPUTADO;
    }

    // ─── Getters y Setters ────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(String pedidoId) {
        this.pedidoId = pedidoId;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public String getResolucion() {
        return resolucion;
    }

    public void setResolucion(String resolucion) {
        this.resolucion = resolucion;
    }

    public String getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(String creadoEn) {
        this.creadoEn = creadoEn;
    }

    public String getResoltoEn() {
        return resoltoEn;
    }

    public void setResoltoEn(String resoltoEn) {
        this.resoltoEn = resoltoEn;
    }
}
