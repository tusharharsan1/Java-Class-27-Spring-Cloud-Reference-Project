package com.scaler.productcatalogservice.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scaler.productcatalogservice.config.SecurityConfig;
import com.scaler.productcatalogservice.dtos.ProductDto;
import com.scaler.productcatalogservice.mappers.ProductMapper;
import com.scaler.productcatalogservice.models.Product;
import com.scaler.productcatalogservice.services.IProductService;
import com.scaler.productcatalogservice.services.PitchService;
import com.scaler.productcatalogservice.services.ProductReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProductController.class)
@Import({ProductMapper.class, SecurityConfig.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IProductService productService;

    @MockitoBean
    private ProductReader productReader;

    @MockitoBean
    private PitchService pitchService;


    // A login token that the filter has already accepted. The real check of the signature is not run here.
    private static final RequestPostProcessor CUSTOMER = jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    @Test
    void getProductById_existingProduct_returns200AndTheJson() throws Exception {
        UUID id = UUID.randomUUID();
        ProductDto dto = new ProductDto();
        dto.setId(id);
        dto.setName("Mascara");
        dto.setPrice(new BigDecimal("9.99"));
        when(productReader.read(id)).thenReturn(dto);

        mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Mascara"))
                .andExpect(jsonPath("$.price").value(9.99));
    }

    @Test
    void getProductById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProductById_notAUuid_returns400() throws Exception {
        mockMvc.perform(get("/products/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_noToken_returns401AndNothingIsSaved() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mascara\"}"))
                .andExpect(status().isUnauthorized());

        verify(productService, never()).createProduct(any());
    }

    @Test
    void deleteProduct_notAnAdmin_returns403AndNothingIsDeleted() throws Exception {
        mockMvc.perform(delete("/products/{id}", UUID.randomUUID()).with(CUSTOMER))
                .andExpect(status().isForbidden());

        verify(productService, never()).deleteProduct(any());
    }

    @Test
    void createProduct_withAToken_returns201AndTheNewProduct() throws Exception {
        UUID id = UUID.randomUUID();
        Product created = new Product();
        created.setId(id);
        created.setName("Mascara");
        when(productService.createProduct(any(Product.class))).thenReturn(created);

        mockMvc.perform(post("/products")
                        .with(CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mascara\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Mascara"));
    }

    @Test
    void replaceProduct_knownId_returns200AndTheReplacedProduct() throws Exception {
        UUID id = UUID.randomUUID();
        Product replaced = new Product();
        replaced.setId(id);
        replaced.setName("Lash Primer");
        when(productService.replaceProduct(any(Product.class), eq(id))).thenReturn(replaced);

        mockMvc.perform(put("/products/{id}", id)
                        .with(CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lash Primer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lash Primer"));
    }

    @Test
    void replaceProduct_unknownId_returns404() throws Exception {
        mockMvc.perform(put("/products/{id}", UUID.randomUUID())
                        .with(CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lash Primer\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_knownId_returns200AndTheUpdatedProduct() throws Exception {
        UUID id = UUID.randomUUID();
        Product updated = new Product();
        updated.setId(id);
        updated.setName("Lash Primer");
        when(productService.updateProduct(any(ProductDto.class), eq(id))).thenReturn(updated);

        mockMvc.perform(patch("/products/{id}", id)
                        .with(CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lash Primer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lash Primer"));
    }

    @Test
    void updateProduct_unknownId_returns404() throws Exception {
        mockMvc.perform(patch("/products/{id}", UUID.randomUUID())
                        .with(CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lash Primer\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_adminAndKnownId_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.deleteProduct(id)).thenReturn(true);

        mockMvc.perform(delete("/products/{id}", id)
                        .with(ADMIN))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(id);
    }

    @Test
    void deleteProduct_adminAndUnknownId_returns404() throws Exception {
        mockMvc.perform(delete("/products/{id}", UUID.randomUUID())
                        .with(ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProductPitch_knownProduct_returnsTheNotification() throws Exception {
        UUID id = UUID.randomUUID();
        Product product = new Product();
        product.setId(id);
        when(productService.getProductById(id)).thenReturn(product);
        when(pitchService.generatePitch(product)).thenReturn("Glow up for $9.99!");

        mockMvc.perform(get("/products/{id}/pitch", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notification").value("Glow up for $9.99!"));
    }

    @Test
    void getProductPitch_unknownProduct_returns404() throws Exception {
        mockMvc.perform(get("/products/{id}/pitch", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProducts_withMinPrice_returnsAPageOfProducts() throws Exception {
        Product product = new Product();
        product.setName("Mascara");
        when(productService.searchProducts(eq(new BigDecimal("100")), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        mockMvc.perform(get("/products").param("minPrice", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mascara"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }
}
