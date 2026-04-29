package com.rappifavor.model;

/**
 * Roles de usuario en el sistema.
 * Definición en SRS Sección 2.3 — Clases de Usuario y Características.
 */
public enum RolUsuario {

    /** Solicita favores. Crea pedidos, consulta historial, puede abrir disputas. */
    USUARIO,

    /** Acepta y realiza favores. Ve pedidos disponibles, actualiza estados. */
    REPARTIDOR,

    /** Supervisa usuarios, pedidos y disputas. Resuelve controversias. */
    ADMINISTRADOR
}
