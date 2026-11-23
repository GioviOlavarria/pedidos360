package com.pedidos360.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Migración única: las bases creadas con versiones anteriores guardan orders.status como ENUM nativo de MySQL,
 * que no acepta el estado nuevo PENDIENTE_PAGO (ddl-auto=update no modifica columnas existentes).
 * Si la columna es ENUM se convierte a VARCHAR(20); en cualquier otro caso no hace nada.
 */
@Component
public class OrderStatusColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusColumnMigration.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public OrderStatusColumnMigration(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        }
        List<String> types = jdbc.queryForList("""
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'status'""", String.class);
        if (types.contains("enum")) {
            jdbc.execute("ALTER TABLE orders MODIFY status VARCHAR(20) NOT NULL");
            log.info("Migración: orders.status convertido de ENUM a VARCHAR(20)");
        }
    }
}
