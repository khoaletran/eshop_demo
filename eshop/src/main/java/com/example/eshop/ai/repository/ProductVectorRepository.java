package com.example.eshop.ai.repository;

import com.example.eshop.ai.model.ProductVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductVectorRepository extends JpaRepository<ProductVector, Long> {

    Optional<ProductVector> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Query("SELECT pv FROM ProductVector pv JOIN FETCH pv.product p JOIN FETCH p.category")
    List<ProductVector> findAllWithProduct();
}
