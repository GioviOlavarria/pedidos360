package com.pedidos360.catalog.controller;

import com.pedidos360.catalog.dto.ProductRequest;
import com.pedidos360.catalog.dto.ProductResponse;
import com.pedidos360.catalog.entity.Product;
import com.pedidos360.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalog")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product product = new Product(request.getName(), request.getDescription(),
                request.getPrice(), request.getStock());
        Product created = productService.create(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> listAll() {
        List<ProductResponse> products = productService.listAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        Product product = productService.getById(id);
        return ResponseEntity.ok(toResponse(product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody ProductRequest request) {
        Product productDetails = new Product(request.getName(), request.getDescription(),
                request.getPrice(), request.getStock());
        Product updated = productService.update(id, productDetails);
        return ResponseEntity.ok(toResponse(updated));
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<Void> decreaseStock(@PathVariable Long id,
                                              @RequestParam Integer quantity) {
        productService.decreaseStock(id, quantity);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(),
                product.getDescription(), product.getPrice(),
                product.getStock());
    }
}