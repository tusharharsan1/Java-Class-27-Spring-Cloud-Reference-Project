package com.scaler.productcatalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.scaler.productcatalogservice.dtos.ProductDto;
import com.scaler.productcatalogservice.models.Product;
import com.scaler.productcatalogservice.repositories.ProductRepo;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// A test on the REAL Redis (database 15, never database 0) and the REAL MySQL test schema.
// It is skipped when no Redis answers on localhost:6379.
// The timeouts are long here on purpose: the first Redis call of a cold test JVM can take more than the 200 ms of the real
// settings, and the cache error handler would then hide it as a miss.
@SpringBootTest(properties = {"spring.data.redis.database=15", "spring.cache.type=redis",
        "spring.data.redis.timeout=5s", "spring.data.redis.connect-timeout=5s"})
@ActiveProfiles("test")
class ProductReaderCacheTest {

    @Autowired
    private ProductReader reader;

    @MockitoSpyBean
    private StorageProductService service;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private StringRedisTemplate redis;

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    private UUID id;

    @BeforeEach
    void prepare() {
        assertThat(databaseUrl).as("this test deletes every product, so it must use the test schema").contains("_test");
        boolean redisAnswers;
        try {
            redis.hasKey("shopnest:ping");
            redisAnswers = true;
        } catch (Exception e) {
            redisAnswers = false;
        }
        assumeThat(redisAnswers).as("no Redis on localhost:6379").isTrue();

        flushDatabase15();
        productRepo.deleteAll();
        Product product = new Product();
        product.setName("Lamp");
        product.setPrice(new BigDecimal("20.00"));
        id = productRepo.save(product).getId();
        clearInvocations(service);
    }

    // The cache saves and deletes on a connection of its own, and the call can return a moment before Redis has done it.
    // A real shop does not care: the key is there, or gone, a few milliseconds later. A test that counts calls must wait.
    private void waitUntilTheKey(UUID key, boolean isThere) throws InterruptedException {
        for (int i = 0; i < 60 && !Boolean.valueOf(isThere).equals(redis.hasKey("shopnest:product::" + key)); i++) {
            Thread.sleep(50);
        }
    }

    @AfterEach
    void cleanUp() {
        flushDatabase15();
    }

    private void flushDatabase15() {
        Set<String> keys = redis.keys("shopnest:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void read_twice_asksTheDatabaseOnceAndSavesReadableJson() throws InterruptedException {
        ProductDto first = reader.read(id);
        waitUntilTheKey(id, true);
        ProductDto second = reader.read(id);

        assertThat(first.getName()).isEqualTo("Lamp");
        assertThat(second.getPrice()).isEqualByComparingTo("20.00");
        verify(service, times(1)).getProductById(id);
        assertThat(redis.opsForValue().get("shopnest:product::" + id)).contains("\"name\":\"Lamp\"");
        assertThat(redis.getExpire("shopnest:product::" + id)).isBetween(1L, 660L);
    }

    @Test
    void updateProduct_removesTheSavedCopy_soTheNextReadSeesTheNewPrice() throws InterruptedException {
        reader.read(id);
        waitUntilTheKey(id, true);
        ProductDto change = new ProductDto();
        change.setPrice(new BigDecimal("25.00"));

        service.updateProduct(change, id);
        waitUntilTheKey(id, false);

        assertThat(redis.hasKey("shopnest:product::" + id)).isFalse();
        assertThat(reader.read(id).getPrice()).isEqualByComparingTo("25.00");
    }

    @Test
    void deleteProduct_removesTheSavedCopy() throws InterruptedException {
        reader.read(id);
        waitUntilTheKey(id, true);

        service.deleteProduct(id);
        waitUntilTheKey(id, false);

        assertThat(redis.hasKey("shopnest:product::" + id)).isFalse();
        assertThat(reader.read(id)).isNull();
    }

    @Test
    void read_anIdThatDoesNotExist_isAskedOnceBecauseTheEmptyAnswerIsSavedToo() throws InterruptedException {
        UUID unknown = UUID.randomUUID();

        assertThat(reader.read(unknown)).isNull();
        waitUntilTheKey(unknown, true);
        assertThat(reader.read(unknown)).isNull();

        verify(service, times(1)).getProductById(unknown);
    }

    @Test
    void keysSavedTogether_haveDifferentLives_soTheyDoNotExpireTogether() throws InterruptedException {
        Set<Long> lives = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            Product product = new Product();
            product.setName("Lamp " + i);
            product.setPrice(new BigDecimal("20.00"));
            UUID other = productRepo.save(product).getId();
            reader.read(other);
            waitUntilTheKey(other, true);
            Long life = redis.getExpire("shopnest:product::" + other);
            assertThat(life).isBetween(540L, 660L);
            lives.add(life);
        }

        assertThat(lives.size()).as("ten minutes plus 0 to 59 random seconds").isGreaterThan(1);
    }
}
