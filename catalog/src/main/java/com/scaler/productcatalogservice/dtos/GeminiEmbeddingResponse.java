package com.scaler.productcatalogservice.dtos;

import java.util.List;

public record GeminiEmbeddingResponse(List<Embedding> embeddings) {
    public record Embedding(List<Float> values) {
    }
}
