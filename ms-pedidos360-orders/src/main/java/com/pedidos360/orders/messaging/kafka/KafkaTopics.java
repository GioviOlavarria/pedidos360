package com.pedidos360.orders.messaging.kafka;

/**
 * Tópicos Kafka de Pedidos360. Los nombres y parámetros deben coincidir con infra/docker-compose.yml (kafka-init)
 * y con las copias de esta clase en catalog y notifications.
 *
 * <ul>
 *   <li>{@link #ORDER_EVENTS}: historial de eventos del pedido (creado, editado, cambio de estado, eliminado).
 *       Política delete con retención de 7 días: es un flujo de eventos para analítica y reacciones de otros servicios.</li>
 *   <li>{@link #BILLING_RECEIPTS}: boleta electrónica emitida para cada pedido. Política compact: con la clave
 *       orderId Kafka conserva siempre la última boleta de cada pedido.</li>
 * </ul>
 * Los mensajes que fallan tras los reintentos se publican en el tópico con sufijo {@link #DLT_SUFFIX}.
 */
public final class KafkaTopics {

    public static final String ORDER_EVENTS = "pedidos360.orders.events";
    public static final String BILLING_RECEIPTS = "pedidos360.billing.receipts";
    public static final String DLT_SUFFIX = ".DLT";

    public static final int PARTITIONS = 3;
    public static final int REPLICAS = 2;

    private KafkaTopics() {
    }
}
