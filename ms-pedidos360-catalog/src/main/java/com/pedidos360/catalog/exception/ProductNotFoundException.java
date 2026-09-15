package com.pedidos360.catalog.exception;

/**
 * Excepción de dominio lanzada cuando no se encuentra un Product por su id.
 * Reemplaza el manejo ad-hoc por string-matching en RuntimeException.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Producto no encontrado con id: " + id);
    }
}
