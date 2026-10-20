package com.pedidos360.catalog;

import com.pedidos360.catalog.entity.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogApiTest {

    private static final String KEY = "test-internal-key";
    private static final String PRODUCT_JSON = """
            {"name":"Caja","description":"d","price":1000,"stock":5}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductRepository repository;

    private Product product;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        product = repository.save(new Product(null, "Caja", "d", new BigDecimal("1990.00"), 5));
    }

    @Test
    void catalogIsPublic() throws Exception {
        mvc.perform(get("/api/catalog")).andExpect(status().isOk());
    }

    @Test
    void customerCannotCreateProducts() throws Exception {
        mvc.perform(post("/api/catalog").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer")))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateProducts() throws Exception {
        mvc.perform(post("/api/catalog").contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateProducts() throws Exception {
        mvc.perform(post("/api/catalog").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void reserveRequiresInternalKey() throws Exception {
        mvc.perform(post("/internal/stock/reserve").contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(1)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/stock/reserve").header("X-Internal-Api-Key", "otra")
                        .contentType(MediaType.APPLICATION_JSON).content(reserveBody(1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reserveDecreasesStockAndReturnsCatalogPrice() throws Exception {
        mvc.perform(post("/internal/stock/reserve").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(reserveBody(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitPrice").value(1990.00))
                .andExpect(jsonPath("$[0].name").value("Caja"));

        assertEquals(3, repository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void reserveFailsWithoutEnoughStock() throws Exception {
        mvc.perform(post("/internal/stock/reserve").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(reserveBody(6)))
                .andExpect(status().isBadRequest());

        assertEquals(5, repository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void releaseReturnsStock() throws Exception {
        mvc.perform(post("/internal/stock/release").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(reserveBody(4)))
                .andExpect(status().isNoContent());

        assertEquals(9, repository.findById(product.getId()).orElseThrow().getStock());
    }

    private String reserveBody(int quantity) {
        return "{\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}]}";
    }
}
