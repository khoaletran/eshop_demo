package com.example.eshop.ai.model;

import com.example.eshop.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * DTO chứa thông tin sản phẩm kèm điểm số tương đồng (Similarity Score)
 * sau khi tính toán khoảng cách Cosine trong Vector Database.
 */
@Getter
@AllArgsConstructor
public class ScoredProduct {
    private final Product product;
    private final double similarityScore; // Giá trị từ 0.0 đến 1.0 (càng gần 1 càng liên quan)
    private final String reason;          // Lý do AI gợi ý (vd: "Phù hợp với sở thích xem iPhone của bạn")

    public Long getId() {
        return product.getId();
    }

    public String getName() {
        return product.getName();
    }

    public String getCategoryName() {
        return product.getCategory() != null ? product.getCategory().getName() : "";
    }

    public BigDecimal getPrice() {
        return product.getPrice();
    }

    public String getImageUrl() {
        return product.getImageUrl();
    }

    public String getDescription() {
        return product.getDescription();
    }
}
