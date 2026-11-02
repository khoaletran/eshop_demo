package com.example.eshop.controller;

import com.example.eshop.ai.recommendation.ProductRecommendationService;
import com.example.eshop.ai.tracking.UserTrackingService;
import com.example.eshop.entity.Product;
import com.example.eshop.service.CategoryService;
import com.example.eshop.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class ShopController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final UserTrackingService trackingService;
    private final ProductRecommendationService recommendationService;

    @GetMapping({"/", "/products"})
    public String index(@RequestParam(required = false) String keyword,
                        @RequestParam(required = false) Long categoryId,
                        @RequestParam(defaultValue = "0") int page,
                        Model model,
                        HttpServletRequest request,
                        HttpServletResponse response) {
        String sid = trackingService.resolveSessionId(request, response);

        // Tracking tìm kiếm từ khóa
        if (keyword != null && !keyword.isBlank()) {
            trackingService.trackSearch(sid, keyword.trim(), categoryId);
        }

        model.addAttribute("products", productService.search(keyword, categoryId, page, 12));
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);

        // AI Recommendations cho người dùng dựa trên hành vi
        model.addAttribute("aiRecommendations", recommendationService.recommendForSession(sid, 4));

        return "shop/index";
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable Long id,
                         Model model,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        String sid = trackingService.resolveSessionId(request, response);
        Product product = productService.findById(id);

        // Ghi nhận sự kiện xem sản phẩm (VIEW) vào User Tracking
        Long catId = product.getCategory() != null ? product.getCategory().getId() : null;
        var event = trackingService.trackView(sid, id, catId, 0);

        model.addAttribute("product", product);
        model.addAttribute("trackingEventId", event != null ? event.getId() : null);
        // Gợi ý các sản phẩm tương tự từ Vector Database (Item-to-Item Similarity)
        model.addAttribute("similarProducts", recommendationService.recommendSimilarProducts(id, 4));

        return "shop/detail";
    }
}
