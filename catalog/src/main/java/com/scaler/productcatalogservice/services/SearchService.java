package com.scaler.productcatalogservice.services;

import com.scaler.productcatalogservice.dtos.SearchRequestDto;
import com.scaler.productcatalogservice.models.Product;
import com.scaler.productcatalogservice.models.State;
import com.scaler.productcatalogservice.repositories.ProductRepo;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final Set<String> SORTABLE = Set.of("name", "price");
    private static final int MAX_SIZE = 50;

    private final ProductRepo productRepo;

    public Page<Product> search(SearchRequestDto request) {
        String word = StringUtils.hasText(request.getQuery()) ? "%" + request.getQuery().trim() + "%" : null;
        return productRepo.search(State.ACTIVE, word, request.getMaxPrice(), pageable(request));
    }

    // Only known fields may be sorted. Size has a maximum. The id is the last sort field, so the order is the same every time.
    private Pageable pageable(SearchRequestDto request) {
        String sortBy = request.getSortBy() == null ? "name" : request.getSortBy();
        if (!SORTABLE.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can sort by name or price, not by '" + sortBy + "'.");
        }
        int page = request.getPage() == null ? 0 : Math.max(request.getPage(), 0);
        int size = request.getSize() == null ? 20 : Math.min(Math.max(request.getSize(), 1), MAX_SIZE);
        return PageRequest.of(page, size, Sort.by(sortBy).and(Sort.by("id")));
    }
}
