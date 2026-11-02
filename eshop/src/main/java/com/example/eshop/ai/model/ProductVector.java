package com.example.eshop.ai.model;

import com.example.eshop.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ dữ liệu Vector Embedding của sản phẩm trong Vector Database.
 *
 * MỤC ĐÍCH CỦA TỪNG TRƯỜNG DỮ LIỆU:
 * 1. id: Khóa chính bản ghi vector.
 * 2. product: Khóa ngoại liên kết trực tiếp tới thực thể Sản phẩm (Product).
 * 3. documentText: Đoạn văn bản đại diện ngữ nghĩa của sản phẩm (Tên, Danh mục, Giá, Mô tả).
 *    -> Dùng để tái tạo hoặc kiểm tra nội dung trước khi chuyển đổi sang vector.
 * 4. embeddingJson: Chuỗi JSON lưu mảng số thực (float[] vector với số chiều xác định, vd: 384 hoặc 768).
 *    -> Đây là tọa độ ngữ nghĩa của sản phẩm trong không gian đa chiều, dùng để tính toán Cosine Similarity.
 * 5. updatedAt: Thời điểm cập nhật vector gần nhất.
 *    -> Giúp nhận biết khi nào thông tin sản phẩm (giá, mô tả) đã thay đổi để re-index vector.
 */
@Entity
@Table(name = "product_vector")
@Getter @Setter @NoArgsConstructor
public class ProductVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Lob
    @Column(name = "document_text", nullable = false, columnDefinition = "TEXT")
    private String documentText;

    @Lob
    @Column(name = "embedding_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String embeddingJson;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ProductVector(Product product, String documentText, String embeddingJson) {
        this.product = product;
        this.documentText = documentText;
        this.embeddingJson = embeddingJson;
    }
}
