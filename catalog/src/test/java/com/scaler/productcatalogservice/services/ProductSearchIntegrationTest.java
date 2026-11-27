package com.scaler.productcatalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.scaler.productcatalogservice.models.Product;
import com.scaler.productcatalogservice.models.State;
import com.scaler.productcatalogservice.repositories.ProductRepo;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

// A test on the REAL MySQL (a separate test schema). Flyway builds the schema from V1 and V2.
// The Mockito test only checked WHICH method the service calls. This test checks the ROWS that come back.
@SpringBootTest
@ActiveProfiles("test")
class ProductSearchIntegrationTest {

    @Autowired
    private StorageProductService service;

    @Autowired
    private ProductRepo productRepo;

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @BeforeEach
    void emptyTheTestSchema() {
        assertThat(databaseUrl).as("this test deletes every product, so it must use the test schema").contains("_test");
        productRepo.deleteAll();
        productRepo.save(productWithPrice("Pen", "50.00"));
        productRepo.save(productWithPrice("Lamp", "150.00"));
        productRepo.save(productWithPrice("Chair", "250.00"));
    }

    @Test
    void searchProducts_onlyMinPrice_returnsTheProductsFromThatPriceUp() {
        Page<Product> page = service.searchProducts(new BigDecimal("100"), null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getName).containsExactlyInAnyOrder("Lamp", "Chair");
    }

    @Test
    void searchProducts_onlyMaxPrice_returnsTheProductsUpToThatPrice() {
        Page<Product> page = service.searchProducts(null, new BigDecimal("150"), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getName).containsExactlyInAnyOrder("Pen", "Lamp");
    }

    @Test
    void searchProducts_noPrices_returnsAllProducts() {
        Page<Product> page = service.searchProducts(null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    private Product productWithPrice(String name, String price) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    @Test
    void search_optionalBoxes_anEmptyBoxIsIgnored() {
        productRepo.save(productWithPrice("Red Lipstick", "120.00"));
        productRepo.save(productWithPrice("Lamp", "20.00"));

        Page<Product> all = productRepo.search(State.ACTIVE, null, null, PageRequest.of(0, 10, Sort.by("price")));
        Page<Product> word = productRepo.search(State.ACTIVE, "%lip%", null, PageRequest.of(0, 10));
        Page<Product> cheap = productRepo.search(State.ACTIVE, null, new BigDecimal("100"), PageRequest.of(0, 10));
        Page<Product> both = productRepo.search(State.ACTIVE, "%a%", new BigDecimal("100"), PageRequest.of(0, 10, Sort.by("name")));

        assertThat(all.getTotalElements()).isEqualTo(5);
        assertThat(word.getContent()).extracting(Product::getName).containsExactly("Red Lipstick");
        assertThat(cheap.getContent()).extracting(Product::getName).containsExactlyInAnyOrder("Pen", "Lamp");
        assertThat(both.getContent()).extracting(Product::getName).containsExactly("Lamp");
    }
}
