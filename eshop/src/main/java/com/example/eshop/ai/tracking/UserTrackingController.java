package com.example.eshop.ai.tracking;

import com.example.eshop.ai.model.EventType;
import com.example.eshop.ai.model.UserEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class UserTrackingController {

    private final UserTrackingService trackingService;

    @Getter @Setter
    public static class EventRequest {
        private String eventType;
        private Long productId;
        private Long categoryId;
        private String keyword;
        private Integer durationSeconds;
    }

    @PostMapping("/api/tracking/event")
    @ResponseBody
    public ResponseEntity<?> receiveEvent(@RequestBody EventRequest req,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        String sid = trackingService.resolveSessionId(request, response);
        if (req.getEventType() != null) {
            try {
                EventType type = EventType.valueOf(req.getEventType().toUpperCase());
                switch (type) {
                    case VIEW -> trackingService.trackView(sid, req.getProductId(), req.getCategoryId(),
                            req.getDurationSeconds() != null ? req.getDurationSeconds() : 0);
                    case SEARCH -> trackingService.trackSearch(sid, req.getKeyword(), req.getCategoryId());
                    case ADD_TO_CART -> trackingService.trackAddToCart(sid, req.getProductId(), req.getCategoryId());
                    case PURCHASE -> {} // Được kích hoạt từ luồng checkout backend
                }
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("status", "ok", "sessionId", sid));
    }

    /**
     * Trang Dashboard quản trị và kiểm tra User Tracking dành cho giáo viên và người chấm điểm.
     */
    @GetMapping("/admin/tracking")
    public String trackingDashboard(Model model) {
        model.addAttribute("stats", trackingService.getTrackingStats());
        model.addAttribute("recentEvents", trackingService.getAllRecentEvents(50));
        return "admin/tracking/index";
    }
}
