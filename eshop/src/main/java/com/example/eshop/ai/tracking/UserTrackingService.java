package com.example.eshop.ai.tracking;

import com.example.eshop.ai.model.EventType;
import com.example.eshop.ai.model.UserEvent;
import com.example.eshop.ai.repository.UserEventRepository;
import com.example.eshop.entity.Order;
import com.example.eshop.entity.OrderDetail;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Dịch vụ theo dõi hành vi người dùng (User Tracking Service).
 *
 * THU THẬP NHỮNG GÌ CỦA NGƯỜI DÙNG?
 * 1. VIEW: Khi người dùng xem chi tiết sản phẩm -> Lưu productId, categoryId, dwellTime (thời gian xem).
 * 2. SEARCH: Khi người dùng tìm kiếm từ khóa -> Lưu keyword, categoryId được lọc.
 * 3. ADD_TO_CART: Khi bấm nút "Thêm vào giỏ" -> Lưu productId, categoryId.
 * 4. PURCHASE: Khi hoàn tất đơn hàng -> Lưu danh sách sản phẩm đã mua.
 * 5. Phiên duyệt web (Session ID / Cookie ID): Nhận diện người dùng xuyên suốt phiên kể cả khi chưa đăng nhập.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTrackingService {

    private final UserEventRepository userEventRepository;

    @Value("${app.ai.tracking.enabled:true}")
    private boolean trackingEnabled;

    public static final String SESSION_COOKIE_NAME = "eshop_sid";

    /**
     * Lấy hoặc khởi tạo Session ID cho người dùng (từ Cookie hoặc HttpSession)
     */
    public String resolveSessionId(HttpServletRequest request, HttpServletResponse response) {
        // 1. Kiểm tra Cookie
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (SESSION_COOKIE_NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }

        // 2. Kiểm tra HttpSession
        HttpSession session = request.getSession(true);
        String sid = (String) session.getAttribute(SESSION_COOKIE_NAME);
        if (sid == null || sid.isBlank()) {
            sid = UUID.randomUUID().toString().replace("-", "");
            session.setAttribute(SESSION_COOKIE_NAME, sid);

            // Ghi nhận cookie phản hồi cho trình duyệt lưu trong 30 ngày
            Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sid);
            cookie.setPath("/");
            cookie.setMaxAge(30 * 24 * 3600);
            cookie.setHttpOnly(false); // Cho phép frontend JS đọc để gửi beacon
            response.addCookie(cookie);
        }
        return sid;
    }

    /**
     * Ghi nhận sự kiện Xem sản phẩm (VIEW)
     */
    @Transactional
    public UserEvent trackView(String sessionId, Long productId, Long categoryId, int durationSeconds) {
        if (!trackingEnabled || sessionId == null || productId == null) return null;
        UserEvent event = new UserEvent(sessionId, EventType.VIEW, productId, categoryId, null);
        event.setDurationSeconds(Math.max(durationSeconds, 0));
        return userEventRepository.save(event);
    }

    /**
     * Ghi nhận sự kiện Tìm kiếm (SEARCH)
     */
    @Transactional
    public UserEvent trackSearch(String sessionId, String keyword, Long categoryId) {
        if (!trackingEnabled || sessionId == null || keyword == null || keyword.isBlank()) return null;
        UserEvent event = new UserEvent(sessionId, EventType.SEARCH, null, categoryId, keyword.trim());
        return userEventRepository.save(event);
    }

    /**
     * Ghi nhận sự kiện Thêm vào giỏ hàng (ADD_TO_CART)
     */
    @Transactional
    public UserEvent trackAddToCart(String sessionId, Long productId, Long categoryId) {
        if (!trackingEnabled || sessionId == null || productId == null) return null;
        UserEvent event = new UserEvent(sessionId, EventType.ADD_TO_CART, productId, categoryId, null);
        return userEventRepository.save(event);
    }

    /**
     * Ghi nhận sự kiện Mua hàng thành công (PURCHASE)
     */
    @Transactional
    public void trackPurchase(String sessionId, Order order) {
        if (!trackingEnabled || sessionId == null || order == null || order.getDetails() == null) return;
        for (OrderDetail detail : order.getDetails()) {
            Long prodId = detail.getProduct() != null ? detail.getProduct().getId() : null;
            Long catId = (detail.getProduct() != null && detail.getProduct().getCategory() != null)
                    ? detail.getProduct().getCategory().getId() : null;

            UserEvent event = new UserEvent(sessionId, EventType.PURCHASE, prodId, catId, null);
            userEventRepository.save(event);
        }
    }

    /**
     * Cập nhật thời gian dừng lại xem trang (Dwell Time)
     */
    @Transactional
    public void updateDuration(Long eventId, int durationSeconds) {
        userEventRepository.findById(eventId).ifPresent(e -> {
            e.setDurationSeconds(durationSeconds);
            userEventRepository.save(e);
        });
    }

    /**
     * Lấy các sự kiện gần đây của một Session
     */
    public List<UserEvent> getRecentEvents(String sessionId, int limit) {
        return userEventRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, limit));
    }

    /**
     * Lấy các sự kiện của một session trong N ngày gần nhất
     */
    public List<UserEvent> getEventsAfter(String sessionId, LocalDateTime after) {
        return userEventRepository.findBySessionIdAndCreatedAtAfterOrderByCreatedAtDesc(sessionId, after);
    }

    /**
     * Lấy danh sách ID các sản phẩm đã mua hoặc trong giỏ hàng để loại trừ khi gợi ý
     */
    public Set<Long> getExcludedProductIds(String sessionId) {
        Set<Long> ids = new HashSet<>();
        ids.addAll(userEventRepository.findPurchasedProductIds(sessionId));
        ids.addAll(userEventRepository.findCartProductIds(sessionId));
        return ids;
    }

    /**
     * Đếm tổng số sự kiện phục vụ trang giám sát thống kê
     */
    public Map<String, Long> getTrackingStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", userEventRepository.count());
        for (EventType type : EventType.values()) {
            stats.put(type.name(), userEventRepository.countByEventType(type));
        }
        return stats;
    }

    public List<UserEvent> getAllRecentEvents(int limit) {
        return userEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }
}
