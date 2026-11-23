package com.pedidos360.catalog.kafka;

/** Tipo de cambio que se publica en el tópico de eventos de pedidos. */
public enum OrderEventType {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    DELETED
}
