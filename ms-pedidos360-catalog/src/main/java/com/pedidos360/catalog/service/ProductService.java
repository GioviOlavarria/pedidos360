package com.pedidos360.catalog.service;

import com.pedidos360.catalog.entity.Product;
import com.pedidos360.catalog.exception.InsufficientStockException;
import com.pedidos360.catalog.exception.ProductNotFoundException;
import com.pedidos360.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product create(Product product) {
        return productRepository.save(product);
    }

    public List<Product> listAll() {
        return productRepository.findAll();
    }

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
        Product product = getById(productId);

        if (product.getStock() < quantity) {
            throw new InsufficientStockException(
                    "Stock insuficiente para el producto " + productId +
                            ". Disponible: " + product.getStock() +
                            ", Solicitado: " + quantity
            );
        }

        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }
}