package com.pedidos360.catalog.config;

import com.pedidos360.catalog.entity.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Carga productos de ejemplo cuando el catálogo está vacío, para que las compras
 * ficticias funcionen desde el primer arranque. Se desactiva con CATALOG_SEED=false.
 */
@Component
@ConditionalOnProperty(name = "app.catalog.seed-sample-data", havingValue = "true")
public class SampleDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final ProductRepository productRepository;

    public SampleDataSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) return;

        List<Product> products = List.of(
                product("Caja Embalaje Reforzada 40x30x25", "Caja de cartón corrugado doble para envíos seguros y resistentes.", 2990, 45),
                product("Cinta Adhesiva de Embalaje Industrial", "Cinta de polipropileno resistente para sellado hermético de encomiendas.", 1490, 120),
                product("Rollo Plástico Burbuja 50cm x 10m", "Protección acolchada contra impactos para productos frágiles y delicados.", 4990, 32),
                product("Etiquetas Autoadhesivas Térmicas 100x150", "Rollo de 500 etiquetas para rotulación logística y guías de despacho.", 6990, 18),
                product("Sobre Acolchado Kraft A4 con Burbuja", "Sobre de seguridad para documentos y piezas pequeñas con cierre adhesivo.", 990, 85),
                product("Dispensador Manual de Cinta de Embalar", "Aplicador ergonómico metálico con cuchilla dentada retráctil.", 8490, 14));
        productRepository.saveAll(products);
        log.info("Catálogo vacío: se cargaron {} productos de ejemplo", products.size());
    }

    private static Product product(String name, String description, int price, int stock) {
        return new Product(null, name, description, BigDecimal.valueOf(price), stock);
    }
}
