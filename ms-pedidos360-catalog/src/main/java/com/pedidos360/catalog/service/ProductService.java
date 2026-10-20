package com.pedidos360.catalog.service;

import com.pedidos360.catalog.dto.ReservedItem;
import com.pedidos360.catalog.dto.StockItem;
import com.pedidos360.catalog.entity.Product;
import com.pedidos360.catalog.exception.InsufficientStockException;
import com.pedidos360.catalog.exception.ProductNotFoundException;
import com.pedidos360.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product create(Product product) {
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> listAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product update(Long id, Product productDetails) {
        Product product = getById(id);
        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setPrice(productDetails.getPrice());
        product.setStock(productDetails.getStock());
        return productRepository.save(product);
    }

    @Transactional
    public void decreaseStock(Long productId, Integer quantity) {
        reserve(List.of(new StockItem(productId, quantity)));
    }

    /**
     * Descuenta stock de varios productos en una sola transacción (todo o nada).
     * Los productos se bloquean en orden de id para evitar deadlocks entre compras concurrentes.
     *
     * @return precio y nombre vigentes de cada producto reservado
     */
    @Transactional
    public List<ReservedItem> reserve(List<StockItem> items) {
        List<ReservedItem> reserved = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : aggregate(items).entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            Product product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));

            if (product.getStock() < quantity) {
                throw new InsufficientStockException(
                        "Stock insuficiente para \"" + product.getName() + "\". Disponible: "
                                + product.getStock() + ", solicitado: " + quantity);
            }

            product.setStock(product.getStock() - quantity);
            reserved.add(new ReservedItem(product.getId(), product.getName(), product.getPrice(), quantity));
        }
        return reserved;
    }

    /** Devuelve stock reservado previamente (pedido cancelado, eliminado o fallido). */
    @Transactional
    public void release(List<StockItem> items) {
        for (Map.Entry<Long, Integer> entry : aggregate(items).entrySet()) {
            productRepository.findByIdForUpdate(entry.getKey()).ifPresentOrElse(
                    product -> product.setStock(product.getStock() + entry.getValue()),
                    () -> log.warn("No se pudo devolver stock: producto {} ya no existe", entry.getKey()));
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }

    private static Map<Long, Integer> aggregate(List<StockItem> items) {
        Map<Long, Integer> byProduct = new TreeMap<>();
        for (StockItem item : items) {
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser positiva");
            }
            byProduct.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return byProduct;
    }
}
