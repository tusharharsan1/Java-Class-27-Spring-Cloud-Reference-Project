package com.scaler.productcatalogservice.repositories;

import com.scaler.productcatalogservice.models.Product;
import com.scaler.productcatalogservice.models.State;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ProductRepo extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndState(UUID id, State state);

    List<Product> findAllByState(State state);

    @EntityGraph(attributePaths = "category")
    Page<Product> findAllByState(State state, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Product> findByPriceBetweenAndState(BigDecimal minPrice, BigDecimal maxPrice, State state, Pageable pageable);

    @Query("select avg(p.price) from Product p where p.category.name = :categoryName and p.state = :state")
    Double findAveragePriceInCategory(String categoryName, State state);

    // An empty box (null) is ignored: ":word is null or ..." is true when the customer typed nothing.
    @EntityGraph(attributePaths = "category")
    @Query("select p from Product p where p.state = :state "
            + "and (:word is null or p.name like :word or p.description like :word) "
            + "and (:maxPrice is null or p.price <= :maxPrice)")
    Page<Product> search(State state, String word, BigDecimal maxPrice, Pageable pageable);
}
