package com.rappifavor.model;

/**
 * Estados del AFD del ciclo de vida de un pedido.
 * Definición formal en SRS Sección 6.1.2.
 *
 * Q = { q0=INICIO, q1=PENDIENTE, q2=ASIGNADO, q3=EN_CAMINO,
 *       q4=ENTREGADO, q5=CANCELADO, q6=DISPUTADO, q7=RESUELTO }
 *
 * Estados finales (F): ENTREGADO, CANCELADO, RESUELTO
 * (ENTREGADO acepta además la transición abrir_disputa → DISPUTADO)
 */
public enum EstadoPedido {

    /** q0 — Estado inicial al crearse el contexto del pedido. */
    INICIO,

    /** q1 — Pedido publicado, sin repartidor asignado. */
    PENDIENTE,

    /** q2 — Repartidor aceptó el pedido. */
    ASIGNADO,

    /** q3 — Repartidor inició la entrega. */
    EN_CAMINO,

    /** q4 — Repartidor confirmó la entrega. Estado final de aceptación. */
    ENTREGADO,

    /** q5 — Usuario canceló el pedido. Estado final de aceptación. */
    CANCELADO,

    /** q6 — Usuario abrió una disputa sobre el pedido entregado. */
    DISPUTADO,

    /** q7 — Administrador resolvió la disputa. Estado final de aceptación. */
    RESUELTO
}
