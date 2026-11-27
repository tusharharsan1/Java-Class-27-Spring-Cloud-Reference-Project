package com.scaler.productcatalogservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scaler.productcatalogservice.clients.EmbeddingClient;
import com.scaler.productcatalogservice.models.Category;
import com.scaler.productcatalogservice.models.Product;
import com.scaler.productcatalogservice.models.State;
import com.scaler.productcatalogservice.repositories.ProductRepo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class ProductEmbedderTest {

    @Mock
    private ProductRepo productRepo;
    @Mock
    private EmbeddingClient embeddingClient;
    @InjectMocks
    private ProductEmbedder embedder;

    @Test
    void run_embedsEveryActiveProductAndSavesTheNumbersOnTheProduct() {
        Category beauty = new Category();
        beauty.setName("beauty");
        Product lipstick = product("Red Lipstick", "A bright red lipstick", beauty);
        Product lamp = product("Lamp", "A lamp", null);
        when(productRepo.findAllByState(State.ACTIVE)).thenReturn(List.of(lipstick, lamp));
        when(embeddingClient.embed(any(), eq("RETRIEVAL_DOCUMENT"))).thenReturn(List.of(new float[]{1f, 0f}, new float[]{0f, 1f}));

        embedder.run(null);

        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(embeddingClient).embed(texts.capture(), eq("RETRIEVAL_DOCUMENT"));
        assertEquals("Red Lipstick. A bright red lipstick Category: beauty", texts.getValue().get(0));
        assertEquals("Lamp. A lamp", texts.getValue().get(1));
        assertEquals("1.0,0.0", lipstick.getEmbedding());
        assertEquals("0.0,1.0", lamp.getEmbedding());
        verify(productRepo).save(lipstick);
        verify(productRepo).save(lamp);
    }

    @Test
    void run_whenTheNumbersServiceSaysTooManyRequests_waitsAndTriesAgain() {
        embedder.waitSeconds = 0;
        Product lamp = product("Lamp", "A lamp", null);
        when(productRepo.findAllByState(State.ACTIVE)).thenReturn(List.of(lamp));
        when(embeddingClient.embed(any(), eq("RETRIEVAL_DOCUMENT")))
                .thenThrow(tooManyRequests())
                .thenReturn(List.of(new float[]{1f, 0f}));

        embedder.run(null);

        verify(embeddingClient, times(2)).embed(any(), eq("RETRIEVAL_DOCUMENT"));
        assertEquals("1.0,0.0", lamp.getEmbedding());
    }

    @Test
    void run_whenTheNumbersServiceKeepsSayingTooManyRequests_givesUpAfterThreeTries() {
        embedder.waitSeconds = 0;
        when(productRepo.findAllByState(State.ACTIVE)).thenReturn(List.of(product("Lamp", "A lamp", null)));
        when(embeddingClient.embed(any(), eq("RETRIEVAL_DOCUMENT"))).thenThrow(tooManyRequests());

        assertThrows(HttpClientErrorException.TooManyRequests.class, () -> embedder.run(null));

        verify(embeddingClient, times(3)).embed(any(), eq("RETRIEVAL_DOCUMENT"));
    }

    private HttpClientErrorException tooManyRequests() {
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", HttpHeaders.EMPTY, new byte[0], null);
    }

    private Product product(String name, String description, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setCategory(category);
        return product;
    }
}
