package com.scaler.productcatalogservice.services;

import com.scaler.productcatalogservice.dtos.ProductDto;
import com.scaler.productcatalogservice.mappers.ProductMapper;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ProductReader {

    private final IProductService productService;
    private final ProductMapper productMapper;

    public ProductReader(IProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    @Cacheable(cacheNames = "product", key = "#id")
    public ProductDto read(UUID id) {
        return productMapper.toDto(productService.getProductById(id));
    }
}
