package com.example.eshop.ai.repository;

import com.example.eshop.ai.model.EventType;
import com.example.eshop.ai.model.UserEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserEventRepository extends JpaRepository<UserEvent, Long> {

    /** Lấy danh sách sự kiện gần nhất của một phiên duyệt web (Session) */
    List<UserEvent> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    /** Lấy các sự kiện của một session diễn ra sau một mốc thời gian nhất định */
    List<UserEvent> findBySessionIdAndCreatedAtAfterOrderByCreatedAtDesc(String sessionId, LocalDateTime after);

    /** Đếm số lượng sự kiện theo từng loại để báo cáo thống kê */
    long countByEventType(EventType eventType);

    /** Lấy các sự kiện gần đây nhất trên toàn hệ thống */
    List<UserEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Lấy danh sách ID các sản phẩm mà người dùng đã mua trong phiên này */
    @Query("SELECT DISTINCT e.productId FROM UserEvent e WHERE e.sessionId = :sessionId AND e.eventType = 'PURCHASE' AND e.productId IS NOT NULL")
    List<Long> findPurchasedProductIds(@Param("sessionId") String sessionId);

    /** Lấy danh sách ID các sản phẩm mà người dùng đã thêm vào giỏ hàng gần đây */
    @Query("SELECT DISTINCT e.productId FROM UserEvent e WHERE e.sessionId = :sessionId AND e.eventType = 'ADD_TO_CART' AND e.productId IS NOT NULL")
    List<Long> findCartProductIds(@Param("sessionId") String sessionId);
}
