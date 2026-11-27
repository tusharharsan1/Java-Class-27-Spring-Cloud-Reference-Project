package com.scaler.productcatalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scaler.productcatalogservice.dtos.ProductDto;
import com.scaler.productcatalogservice.mappers.ProductMapper;
import com.scaler.productcatalogservice.models.Category;
import com.scaler.productcatalogservice.models.Product;
import com.scaler.productcatalogservice.models.State;
import com.scaler.productcatalogservice.repositories.CategoryRepo;
import com.scaler.productcatalogservice.repositories.ProductRepo;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class StorageProductServiceTest {

    @Mock
    private ProductRepo productRepo;

    @Mock
    private CategoryRepo categoryRepo;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private StorageProductService service;

    @Test
    void getProductById_activeProduct_returnsIt() {
        UUID id = UUID.randomUUID();
        Product product = new Product();
        product.setName("Mascara");
        when(productRepo.findByIdAndState(id, State.ACTIVE)).thenReturn(Optional.of(product));

        Product result = service.getProductById(id);

        assertThat(result.getName()).isEqualTo("Mascara");
    }

    @Test
    void createProduct_clientSendsAnId_savesWithoutItAndWithDates() {
        Product input = new Product();
        input.setId(UUID.randomUUID());
        input.setName("Mascara");
        when(productRepo.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        Product result = service.createProduct(input);

        assertThat(result.getName()).isEqualTo("Mascara");
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepo).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getLastUpdatedAt()).isNotNull();
    }

    @Test
    void createProduct_categoryAlreadyExists_usesTheExistingOne() {
        Category existing = new Category();
        existing.setName("beauty");
        when(categoryRepo.findByName("beauty")).thenReturn(Optional.of(existing));
        when(productRepo.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));
        Category fromRequest = new Category();
        fromRequest.setName("beauty");
        Product input = new Product();
        input.setCategory(fromRequest);

        Product result = service.createProduct(input);

        assertThat(result.getCategory()).isSameAs(existing);
    }

    @Test
    void deleteProduct_unknownId_returnsFalseAndSavesNothing() {
        boolean deleted = service.deleteProduct(UUID.randomUUID());

        assertThat(deleted).isFalse();
        verify(productRepo, never()).save(any());
    }

    @Test
    void searchProducts_onlyMinPrice_filtersByMinPrice() {
        Pageable pageable = PageRequest.of(0, 10);

        service.searchProducts(new BigDecimal("100"), null, pageable);

        verify(productRepo).findByPriceBetweenAndState(eq(new BigDecimal("100")), any(BigDecimal.class),
                eq(State.ACTIVE), eq(pageable));
    }

    // The checklist tests (in class, the agent drafts these and we prune them)

    @Test
    void replaceProduct_unknownId_returnsNullAndSavesNothing() {
        Product result = service.replaceProduct(new Product(), UUID.randomUUID());

        assertThat(result).isNull();
        verify(productRepo, never()).save(any());
    }

    @Test
    void replaceProduct_knownId_usesTheIdFromTheUrlAndKeepsCreatedAt() {
        UUID id = UUID.randomUUID();
        Date createdAt = new Date(0);
        Product old = new Product();
        old.setCreatedAt(createdAt);
        when(productRepo.findByIdAndState(id, State.ACTIVE)).thenReturn(Optional.of(old));
        when(productRepo.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        Product result = service.replaceProduct(new Product(), id);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void updateProduct_unknownId_returnsNullAndSavesNothing() {
        Product result = service.updateProduct(new ProductDto(), UUID.randomUUID());

        assertThat(result).isNull();
        verify(productRepo, never()).save(any());
    }

    @Test
    void updateProduct_knownId_appliesThePatchAndSavesOnce() {
        UUID id = UUID.randomUUID();
        Product existing = new Product();
        ProductDto patch = new ProductDto();
        when(productRepo.findByIdAndState(id, State.ACTIVE)).thenReturn(Optional.of(existing));
        when(productMapper.applyPatch(existing, patch)).thenReturn(existing);
        when(productRepo.save(existing)).thenReturn(existing);

        Product result = service.updateProduct(patch, id);

        assertThat(result).isSameAs(existing);
        verify(productRepo, times(1)).save(existing);
    }

    @Test
    void deleteProduct_knownId_marksItInactiveAndSavesIt() {
        UUID id = UUID.randomUUID();
        Product product = new Product();
        when(productRepo.findByIdAndState(id, State.ACTIVE)).thenReturn(Optional.of(product));

        boolean deleted = service.deleteProduct(id);

        assertThat(deleted).isTrue();
        assertThat(product.getState()).isEqualTo(State.INACTIVE);
        verify(productRepo).save(product);
    }

    @Test
    void searchProducts_onlyMaxPrice_filtersFromZeroUpToThatMaximum() {
        Pageable pageable = PageRequest.of(0, 10);

        service.searchProducts(null, new BigDecimal("50"), pageable);

        verify(productRepo).findByPriceBetweenAndState(eq(BigDecimal.ZERO), eq(new BigDecimal("50")),
                eq(State.ACTIVE), eq(pageable));
    }

    @Test
    void searchProducts_noPrices_returnsAllActiveProductsWithoutAFilter() {
        Pageable pageable = PageRequest.of(0, 10);

        service.searchProducts(null, null, pageable);

        verify(productRepo).findAllByState(State.ACTIVE, pageable);
        verify(productRepo, never()).findByPriceBetweenAndState(any(), any(), any(), any());
    }
}
