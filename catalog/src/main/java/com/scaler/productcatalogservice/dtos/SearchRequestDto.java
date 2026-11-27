package com.scaler.productcatalogservice.dtos;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchRequestDto {
    private String query;
    private BigDecimal maxPrice;
    private Integer page;
    private Integer size;
    private String sortBy;
}
