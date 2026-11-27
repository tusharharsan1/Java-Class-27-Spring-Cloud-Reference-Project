package com.scaler.productcatalogservice.services;

import com.scaler.productcatalogservice.clients.EmbeddingClient;
import com.scaler.productcatalogservice.dtos.MeaningHitDto;
import com.scaler.productcatalogservice.dtos.SearchRequestDto;
import com.scaler.productcatalogservice.models.State;
import com.scaler.productcatalogservice.repositories.ProductRepo;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class MeaningSearchService {

    private static final Logger log = LoggerFactory.getLogger(MeaningSearchService.class);

    private final ProductRepo productRepo;
    private final EmbeddingClient embeddingClient;
    private final SearchService searchService;
    private final QueryVectorStore store;

    @Value("${search.lab-direct:false}")
    private boolean labDirect;

    // The numbers of the sentence against the saved numbers of every product. The closest K come back.
    public List<MeaningHitDto> search(String text, int k) {
        try {
            float[] query = vectorOf(text);
            return productRepo.findAllByState(State.ACTIVE).stream()
                    .filter(product -> product.getEmbedding() != null)
                    .map(product -> new MeaningHitDto(product.getName(),
                            cosine(query, EmbeddingClient.toNumbers(product.getEmbedding()))))
                    .sorted(Comparator.comparingDouble(MeaningHitDto::score).reversed())
                    .limit(k)
                    .toList();
        } catch (RestClientException e) {
            // The numbers service is down or slow. The customer still gets an answer, from the plain search.
            log.warn("Meaning search failed ({}). Using the plain search.", e.getMessage());
            SearchRequestDto request = new SearchRequestDto();
            request.setQuery(text);
            request.setSize(k);
            return searchService.search(request).map(product -> new MeaningHitDto(product.getName(), null)).getContent();
        }
    }

    private float[] vectorOf(String text) {
        if (labDirect) {
            return embeddingClient.embed(List.of(text), "RETRIEVAL_QUERY").get(0);
        }
        String key = "shopnest:query:" + text.trim().toLowerCase();
        String saved = store.get(key);
        if (saved != null) {
            return EmbeddingClient.toNumbers(saved);
        }

        boolean mine = store.tryLock(key);
        if (!mine) {
            saved = store.waitFor(key);
            if (saved != null) {
                return EmbeddingClient.toNumbers(saved);
            }
        }
        try {
            log.info("Asking Gemini for the numbers of: {}", text);
            float[] query = embeddingClient.embed(List.of(text), "RETRIEVAL_QUERY").get(0);
            store.save(key, EmbeddingClient.toText(query));
            return query;
        } finally {
            if (mine) {
                store.unlock(key);
            }
        }
    }

    // 1 means the two arrows point the same way. 0 means nothing in common.
    static double cosine(float[] a, float[] b) {
        double dot = 0, lengthA = 0, lengthB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            lengthA += a[i] * a[i];
            lengthB += b[i] * b[i];
        }
        return dot / (Math.sqrt(lengthA) * Math.sqrt(lengthB));
    }
}
