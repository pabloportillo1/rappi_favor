package com.rappifavor.model;

/**
 * Subdocumento embebido en Pedido.
 * Registra cada transición de estado del AFD con su timestamp.
 *
 * Requerimiento: FR-06 — "Se registra timestamp de cada cambio."
 *
 * No tiene _id propio: es un subdocumento dentro del array
 * historial[] del documento Pedido en MongoDB.
 *
 * Compatibilidad:
 *  - MongoDB POJO codec: requiere constructor sin argumentos + getters/setters.
 *  - Gson: serializa/deserializa los campos directamente.
 */
public class HistorialEstado {

    /** Estado del pedido en este punto del AFD. */
    private EstadoPedido estado;

    /** Momento de la transición en formato ISO 8601. Ej: "2026-04-29T14:30:00Z" */
    private String timestamp;

    // ─── Constructor requerido por el POJO codec de MongoDB ──────────────────
    public HistorialEstado() {}

    public HistorialEstado(EstadoPedido estado, String timestamp) {
        this.estado    = estado;
        this.timestamp = timestamp;
    }

    // ─── Getters y Setters ────────────────────────────────────────────────────

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
