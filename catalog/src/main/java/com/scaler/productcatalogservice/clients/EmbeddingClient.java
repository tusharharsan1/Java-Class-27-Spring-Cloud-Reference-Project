package com.scaler.productcatalogservice.clients;

import com.scaler.productcatalogservice.dtos.GeminiEmbeddingResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// The one place that turns a text into numbers (Google Gemini embeddings, free key from AI Studio)
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private static final String MODEL = "gemini-embedding-001";

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;
    private final String url;
    private final boolean discover;

    public EmbeddingClient(RestClient.Builder builder,
                           DiscoveryClient discoveryClient,
                           @Value("${embedding.url:https://generativelanguage.googleapis.com/v1beta}") String url,
                           @Value("${embedding.api-key}") String apiKey,
                           @Value("${embedding.discover:false}") boolean discover) {
        this.discoveryClient = discoveryClient;
        this.url = url;
        this.discover = discover;
        if (!discover && !apiKey.isBlank()) {
            builder.defaultHeader("x-goog-api-key", apiKey);
        }
        this.restClient = builder.build();
    }

    private String embeddingBase() {
        if (!discover) {
            return url;
        }
        List<ServiceInstance> instances = discoveryClient.getInstances("embedding-service");
        // This class uses one embedding instance. Class 28 will teach selection among replicas.
        if (instances.size() != 1) {
            throw new RestClientException("Expected one embedding instance, found " + instances.size());
        }
        String address = instances.get(0).getUri().toString();
        log.info("Discovered embedding address {}", address);
        return address + "/v1beta";
    }

    // One call can carry many texts. kind is RETRIEVAL_DOCUMENT for a product and RETRIEVAL_QUERY for a search.
    public List<float[]> embed(List<String> texts, String kind) {
        List<Map<String, Object>> requests = texts.stream().map(text -> Map.<String, Object>of(
                "model", "models/" + MODEL,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "taskType", kind,
                "outputDimensionality", 768)).toList();
        GeminiEmbeddingResponse response = restClient.post()
                .uri(embeddingBase() + "/models/{model}:batchEmbedContents", MODEL)
                .body(Map.of("requests", requests))
                .retrieve()
                .body(GeminiEmbeddingResponse.class);
        return response.embeddings().stream().map(e -> toNumbers(e.values())).toList();
    }

    // The numbers are saved as one text, like 0.12,-0.03,0.51
    public static String toText(float[] numbers) {
        StringBuilder text = new StringBuilder();
        for (float number : numbers) {
            text.append(text.isEmpty() ? "" : ",").append(number);
        }
        return text.toString();
    }

    public static float[] toNumbers(String text) {
        String[] parts = text.split(",");
        float[] numbers = new float[parts.length];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = Float.parseFloat(parts[i]);
        }
        return numbers;
    }

    private static float[] toNumbers(List<Float> values) {
        float[] numbers = new float[values.size()];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = values.get(i);
        }
        return numbers;
    }
}
