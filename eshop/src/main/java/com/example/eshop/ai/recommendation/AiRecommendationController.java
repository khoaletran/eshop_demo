package com.example.eshop.ai.recommendation;

import com.example.eshop.ai.model.ScoredProduct;
import com.example.eshop.ai.tracking.UserTrackingService;
import com.example.eshop.ai.vector.VectorStoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AiRecommendationController {

    private final ProductRecommendationService recommendationService;
    private final UserTrackingService trackingService;
    private final VectorStoreService vectorStoreService;

    @GetMapping("/api/ai/recommendations")
    @ResponseBody
    public ResponseEntity<List<ScoredProduct>> getRecommendations(
            @RequestParam(defaultValue = "6") int limit,
            HttpServletRequest request,
            HttpServletResponse response) {
        String sid = trackingService.resolveSessionId(request, response);
        List<ScoredProduct> recs = recommendationService.recommendForSession(sid, limit);
        return ResponseEntity.ok(recs);
    }

    @PostMapping("/admin/ai/reindex")
    public String triggerReindex(RedirectAttributes ra) {
        vectorStoreService.reindexAll();
        ra.addFlashAttribute("message", "Đã lập chỉ mục lại thành công toàn bộ Vector Database (" +
                vectorStoreService.getIndexedCount() + " sản phẩm)!");
        return "redirect:/admin/products";
    }
}
