package com.rappifavor.model;

import org.bson.codecs.pojo.annotations.BsonId;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Pedido — colección "pedidos" en MongoDB.
 *
 * Modela una solicitud de favor en la plataforma. Su ciclo de vida
 * está gobernado por el AFD definido en SRS Sección 6.1.
 *
 * El campo historial[] registra cada transición de estado (FR-06).
 * La lógica de transición del AFD vive en OrderService (Fase 4),
 * no en este modelo.
 *
 * Requerimientos: FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-11.
 *
 * Compatibilidad:
 *  - MongoDB POJO codec: constructor sin argumentos + getters/setters requeridos.
 *  - Gson: los tipos List<HistorialEstado> y enums se serializan correctamente.
 */
public class Pedido {

    /**
     * Identificador único. Generado por el OrderRepository como hex string
     * de ObjectId (ej. "663f1a2b4c5d6e7f8a9b0c1d") antes de insertar.
     */
    @BsonId
    private String id;

    /** Descripción del favor solicitado. Campo D de la MT (SRS 6.2). */
    private String descripcion;

    /** Lugar de origen / recogida del favor. */
    private String origen;

    /** Lugar de destino / entrega del favor. Campo T de la MT (SRS 6.2). */
    private String destino;

    /**
     * Estado actual en el AFD.
     * Valor inicial al crear el pedido: EstadoPedido.PENDIENTE (q1).
     * Solo OrderService puede modificar este campo.
     */
    private EstadoPedido estado;

    /**
     * Firebase UID del usuario que creó el pedido.
     * Campo U de la MT (SRS 6.2) — usuario autenticado.
     */
    private String usuarioId;

    /**
     * Firebase UID del repartidor que aceptó el pedido.
     * Es null hasta que un repartidor ejecuta la acción aceptar_pedido (FR-05).
     */
    private String repartidorId;

    /**
     * Registro cronológico de transiciones del AFD.
     * Cada entrada captura el estado y el timestamp del cambio (FR-06).
     * Inicializado como lista vacía para evitar NPE al agregar entradas.
     */
    private List<HistorialEstado> historial;

    /** Fecha y hora de creación del pedido en formato ISO 8601. */
    private String creadoEn;

    /** Fecha y hora de la última modificación en formato ISO 8601. */
    private String actualizadoEn;

    // ─── Constructor requerido por el POJO codec de MongoDB ──────────────────
    public Pedido() {
        this.historial = new ArrayList<>();
    }

    public Pedido(String descripcion, String origen, String destino, String usuarioId) {
        this.descripcion  = descripcion;
        this.origen       = origen;
        this.destino      = destino;
        this.usuarioId    = usuarioId;
        this.estado       = EstadoPedido.PENDIENTE;
        this.historial    = new ArrayList<>();
    }

    // ─── Getters y Setters ────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getRepartidorId() {
        return repartidorId;
    }

    public void setRepartidorId(String repartidorId) {
        this.repartidorId = repartidorId;
    }

    public List<HistorialEstado> getHistorial() {
        return historial;
    }

    public void setHistorial(List<HistorialEstado> historial) {
        this.historial = historial;
    }

    public String getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(String creadoEn) {
        this.creadoEn = creadoEn;
    }

    public String getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(String actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
