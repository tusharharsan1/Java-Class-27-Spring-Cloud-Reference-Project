package com.scaler.embeddingservice.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Stand-in embedding service with the same JSON contract as Gemini batchEmbedContents,
// plus a /chaos delay toggle for the slow-dependency lab in Class 28.
@RestController
public class EmbeddingController {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingController.class);
    private static final int DIMENSIONS = 768;

    private static final Map<String, Integer> CONCEPT_SLOTS = Map.ofEntries(
            Map.entry("eye", 0), Map.entry("eyes", 0), Map.entry("lash", 0), Map.entry("lashes", 0),
            Map.entry("mascara", 0), Map.entry("eyeshadow", 0), Map.entry("makeup", 0), Map.entry("beauty", 0),
            Map.entry("bigger", 0), Map.entry("volumizing", 0),
            Map.entry("lip", 1), Map.entry("lips", 1), Map.entry("lipstick", 1), Map.entry("red", 1),
            Map.entry("polish", 1), Map.entry("nail", 1),
            Map.entry("perfume", 2), Map.entry("fragrance", 2), Map.entry("fragrances", 2), Map.entry("scent", 2),
            Map.entry("floral", 2), Map.entry("eau", 2),
            Map.entry("lamp", 3), Map.entry("lighting", 3), Map.entry("chair", 3), Map.entry("sofa", 3),
            Map.entry("bed", 3), Map.entry("furniture", 3), Map.entry("wood", 3), Map.entry("wooden", 3),
            Map.entry("table", 3), Map.entry("home", 3),
            Map.entry("laptop", 4), Map.entry("phone", 4), Map.entry("smartphone", 4), Map.entry("tablet", 4),
            Map.entry("watch", 4), Map.entry("watches", 4), Map.entry("mobile", 4),
            Map.entry("apple", 5), Map.entry("fruit", 5), Map.entry("juice", 5), Map.entry("coffee", 5),
            Map.entry("groceries", 5), Map.entry("food", 5), Map.entry("honey", 5), Map.entry("milk", 5)
    );

    private final AtomicLong delayMs = new AtomicLong(0);
    private final String port;

    public EmbeddingController(@Value("${server.port:9090}") String port) {
        this.port = port;
    }

    @PostMapping("/v1beta/models/{model}:batchEmbedContents")
    public EmbeddingResponse batchEmbed(@PathVariable String model, @RequestBody BatchRequest body)
            throws InterruptedException {
        long delay = delayMs.get();
        List<SingleRequest> requests = body.requests() == null ? List.of() : body.requests();
        log.info("Embedding request on port {} for {} text(s), delay={}ms", port, requests.size(), delay);
        if (delay > 0) {
            Thread.sleep(delay);
        }
        List<EmbeddingItem> items = new ArrayList<>(requests.size());
        for (SingleRequest req : requests) {
            String text = extractText(req);
            items.add(new EmbeddingItem(vectorFor(text)));
        }
        return new EmbeddingResponse(items);
    }

    @PostMapping("/chaos")
    public Map<String, Object> setChaos(@RequestParam(defaultValue = "0") long delayMs) {
        this.delayMs.set(Math.max(0, delayMs));
        log.warn("Chaos delay on port {} set to {} ms", port, this.delayMs.get());
        return Map.of("port", port, "delayMs", this.delayMs.get());
    }

    @GetMapping("/chaos")
    public Map<String, Object> getChaos() {
        return Map.of("port", port, "delayMs", delayMs.get());
    }

    private static String extractText(SingleRequest req) {
        if (req == null || req.content() == null || req.content().parts() == null || req.content().parts().isEmpty()) {
            return "";
        }
        String text = req.content().parts().get(0).text();
        return text == null ? "" : text;
    }

    static List<Float> vectorFor(String text) {
        double[] raw = new double[DIMENSIONS];
        String[] tokens = text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            Integer concept = CONCEPT_SLOTS.get(token);
            if (concept != null) {
                raw[concept * 10] += 4.0;
            }
            int idx = 100 + Math.floorMod(token.hashCode(), DIMENSIONS - 100);
            raw[idx] += 1.0;
        }
        double norm = 0.0;
        for (double v : raw) {
            norm += v * v;
        }
        norm = norm == 0.0 ? 1.0 : Math.sqrt(norm);
        List<Float> result = new ArrayList<>(DIMENSIONS);
        for (int i = 0; i < DIMENSIONS; i++) {
            float val = (float) (raw[i] / norm);
            result.add(i == 0 && norm == 1.0 ? 1.0f : val);
        }
        return result;
    }

    public record BatchRequest(List<SingleRequest> requests) {}
    public record SingleRequest(String model, Content content, String taskType, Integer outputDimensionality) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}
    public record EmbeddingResponse(List<EmbeddingItem> embeddings) {}
    public record EmbeddingItem(List<Float> values) {}
}
