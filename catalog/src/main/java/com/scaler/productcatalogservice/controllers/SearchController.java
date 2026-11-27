package com.scaler.productcatalogservice.controllers;

import com.scaler.productcatalogservice.dtos.MeaningHitDto;
import com.scaler.productcatalogservice.dtos.ProductDto;
import com.scaler.productcatalogservice.dtos.SearchRequestDto;
import com.scaler.productcatalogservice.mappers.ProductMapper;
import com.scaler.productcatalogservice.services.MeaningSearchService;
import com.scaler.productcatalogservice.services.RateLimiter;
import com.scaler.productcatalogservice.services.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final ProductMapper productMapper;
    private final MeaningSearchService meaningSearchService;
    private final RateLimiter rateLimiter;

    // The search words travel in the body.
    @PostMapping
    public PagedModel<ProductDto> search(@RequestBody SearchRequestDto request) {
        return new PagedModel<>(searchService.search(request).map(productMapper::toDto));
    }

    // The same search words travel in the address. Spring fills the same class from the address.
    @GetMapping
    public PagedModel<ProductDto> searchByAddress(SearchRequestDto request) {
        return new PagedModel<>(searchService.search(request).map(productMapper::toDto));
    }

    // No matching words needed. The closest products by meaning come back, with their score.
    @GetMapping("/meaning")
    public ResponseEntity<?> meaning(@RequestParam String query, Authentication authentication,
                                     HttpServletRequest request) {
        String who = authentication instanceof JwtAuthenticationToken ? "user:" + authentication.getName()
                : "ip:" + request.getRemoteAddr();
        long wait = rateLimiter.secondsToWait(who);
        if (wait > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(wait))
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                            "Too many searches. Try again in " + wait + " seconds."));
        }
        return ResponseEntity.ok(meaningSearchService.search(query, 5));
    }
}
