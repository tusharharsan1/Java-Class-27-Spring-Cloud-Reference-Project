package com.scaler.productcatalogservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scaler.productcatalogservice.dtos.SearchRequestDto;
import com.scaler.productcatalogservice.models.State;
import com.scaler.productcatalogservice.repositories.ProductRepo;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ProductRepo productRepo;
    @InjectMocks
    private SearchService searchService;

    private Pageable pageSentToTheRepo(SearchRequestDto request) {
        when(productRepo.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        searchService.search(request);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepo).search(any(), any(), any(), pageable.capture());
        return pageable.getValue();
    }

    @Test
    void search_wordIsWrappedInPercentSigns_andAnEmptyBoxBecomesNull() {
        when(productRepo.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        SearchRequestDto request = new SearchRequestDto();
        request.setQuery("  mascara ");
        request.setMaxPrice(new BigDecimal("20"));

        searchService.search(request);
        searchService.search(new SearchRequestDto());

        verify(productRepo).search(eq(State.ACTIVE), eq("%mascara%"), eq(new BigDecimal("20")), any(Pageable.class));
        verify(productRepo).search(eq(State.ACTIVE), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void search_withoutPageSizeAndSort_isThe20FirstByNameThenId() {
        Pageable pageable = pageSentToTheRepo(new SearchRequestDto());

        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals("name: ASC,id: ASC", pageable.getSort().toString());
    }

    @Test
    void search_sizeHasAMaximum_negativePageBecomes0_andTheIdIsTheLastSortField() {
        SearchRequestDto request = new SearchRequestDto();
        request.setSize(1000);
        request.setPage(-3);
        request.setSortBy("price");

        Pageable pageable = pageSentToTheRepo(request);

        assertEquals(50, pageable.getPageSize());
        assertEquals(0, pageable.getPageNumber());
        assertEquals("price: ASC,id: ASC", pageable.getSort().toString());
    }

    @Test
    void search_unknownSortField_isRefusedWith400() {
        SearchRequestDto request = new SearchRequestDto();
        request.setSortBy("secret");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> searchService.search(request));

        assertEquals(400, e.getStatusCode().value());
    }
}
