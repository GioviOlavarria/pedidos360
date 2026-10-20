package com.pedidos360.catalog.controller;

import com.pedidos360.catalog.dto.ReservedItem;
import com.pedidos360.catalog.dto.StockRequest;
import com.pedidos360.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Endpoints internos usados por ms-pedidos360-orders para reservar y liberar stock.
 * No se exponen a través del BFF (que solo enruta /api/**) y exigen la cabecera
 * X-Internal-Api-Key compartida entre microservicios.
 */
@RestController
@RequestMapping("/internal/stock")
public class InternalStockController {

    private final ProductService productService;
    private final byte[] internalApiKey;

    public InternalStockController(ProductService productService,
                                   @Value("${app.internal.api-key:}") String internalApiKey) {
        this.productService = productService;
        this.internalApiKey = internalApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/reserve")
    public ResponseEntity<List<ReservedItem>> reserve(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
                                                      @Valid @RequestBody StockRequest request) {
        checkKey(key);
        return ResponseEntity.ok(productService.reserve(request.items()));
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
                                        @Valid @RequestBody StockRequest request) {
        checkKey(key);
        productService.release(request.items());
        return ResponseEntity.noContent().build();
    }

    private void checkKey(String key) {
        if (internalApiKey.length == 0 || key == null
                || !MessageDigest.isEqual(internalApiKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso interno no autorizado");
        }
    }
}
