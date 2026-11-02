package com.example.eshop.ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity lưu vết hành vi người dùng (User Tracking / Interaction Event).
 *
 * MỤC ĐÍCH CỦA TỪNG TRƯỜNG DỮ LIỆU:
 * 1. id: Khóa chính định danh duy nhất cho từng bản ghi sự kiện.
 * 2. sessionId: Định danh phiên duyệt web (Guest Session / Visitor Token) lưu trong Cookie hoặc Session.
 *    -> Mục đích: Thu thập và liên kết chuỗi hành vi của người dùng ngay cả khi họ CHƯA ĐĂNG NHẬP (GUEST).
 * 3. userId: ID tài khoản nếu người dùng đã đăng nhập hoặc cung cấp thông tin.
 * 4. eventType: Loại hành vi (VIEW, SEARCH, ADD_TO_CART, PURCHASE).
 *    -> Mục đích: Phân loại mức độ quan tâm (Intent Level); mỗi hành vi sẽ nhân với một trọng số (Weight) khác nhau.
 * 5. productId: ID sản phẩm được tương tác.
 *    -> Mục đích: Trích xuất Vector đặc trưng (Embedding Vector) của sản phẩm để cập nhật vào Hồ sơ sở thích (User Profile).
 * 6. categoryId: ID danh mục của sản phẩm.
 *    -> Mục đích: Lọc nhanh sở thích danh mục (Category Affinity) và phục vụ chiến lược gợi ý Fallback (Cold-start).
 * 7. keyword: Từ khóa tìm kiếm khi người dùng thực hiện SEARCH.
 *    -> Mục đích: Phân tích ý định tìm kiếm bằng ngữ nghĩa (Semantic Query Intent).
 * 8. durationSeconds: Số giây người dùng dừng lại xem sản phẩm (Dwell Time).
 *    -> Mục đích: Đo lường mức độ quan tâm sâu (Implicit Feedback: xem 60s quan tâm hơn xem 2s).
 * 9. createdAt: Thời điểm xảy ra hành vi.
 *    -> Mục đích CỰC KỲ QUAN TRỌNG TRONG AI:
 *       - Recency Decay (Phân rã theo thời gian): Hành vi vừa xảy ra 5 phút trước có trọng số cao hơn hành vi 2 tuần trước.
 *       - Session Windowing: Gom nhóm các hành vi theo phiên duyệt web (ví dụ: cửa sổ 30 phút).
 * 10. updatedAt: Thời điểm cập nhật bản ghi.
 *    -> Mục đích: Ghi nhận thời điểm client cập nhật bổ sung durationSeconds khi người dùng rời trang,
 *       hoặc đánh dấu thời điểm tiến trình AI xử lý/gộp vector (Batch Aggregation).
 */
@Entity
@Table(name = "user_event")
@Getter @Setter @NoArgsConstructor
public class UserEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "keyword", length = 255)
    private String keyword;

    @Column(name = "duration_seconds")
    private Integer durationSeconds = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserEvent(String sessionId, EventType eventType, Long productId, Long categoryId, String keyword) {
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.productId = productId;
        this.categoryId = categoryId;
        this.keyword = keyword;
    }
}
