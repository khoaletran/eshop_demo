package com.example.eshop.ai.recommendation;

import com.example.eshop.ai.model.EventType;
import com.example.eshop.ai.model.ScoredProduct;
import com.example.eshop.ai.model.UserEvent;
import com.example.eshop.ai.tracking.UserTrackingService;
import com.example.eshop.ai.vector.EmbeddingService;
import com.example.eshop.ai.vector.VectorMath;
import com.example.eshop.ai.vector.VectorStoreService;
import com.example.eshop.entity.Product;
import com.example.eshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dịch vụ gợi ý sản phẩm thông minh bằng AI (AI Product Recommendation Service).
 *
 * KHI ĐÃ CÓ DỮ LIỆU USER TRACKING, AI GỢI Ý SẢN PHẨM NHƯ THẾ NÀO?
 * -------------------------------------------------------------
 * 1. Thu thập chuỗi hành vi trong quá khứ của người dùng (Session Interaction History).
 * 2. Tính toán trọng số hành vi (Behavior Weight):
 *    - VIEW: 1.0 (nhân thêm hệ số thời gian dừng xem - Dwell Time factor).
 *    - SEARCH: 1.5.
 *    - ADD_TO_CART: 3.0 (thể hiện mức độ quan tâm cao).
 *    - PURCHASE: 5.0 (đã mua).
 * 3. Tính toán độ suy giảm theo thời gian (Recency Decay):
 *    - Các hành vi mới diễn ra gần đây phản ánh sở thích hiện tại, có trọng số cao hơn hành vi trong quá khứ.
 *    - Công thức bán rã: Decay(t) = 0.5 ^ (hoursAgo / halfLifeHours).
 * 4. Tạo User Interest Profile Vector (Vector sở thích người dùng):
 *    - UserVector = Tổng sigma( Weight(event_i) * Decay(t_i) * ItemVector_i ).
 *    - Chuẩn hóa vector đơn vị L2.
 * 5. Truy vấn Vector Database (Vector Similarity Search):
 *    - Tính khoảng cách Cosine giữa UserVector và toàn bộ ProductVector trong catalog.
 *    - Lọc bỏ các sản phẩm đã mua / đã có trong giỏ hàng.
 *    - Trả về Top K sản phẩm có điểm tương đồng cao nhất.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRecommendationService {

    private final UserTrackingService trackingService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final ProductRepository productRepository;

    @Value("${app.ai.vector.dimension:384}")
    private int vectorDimension;

    @Value("${app.ai.recommendation.decay-half-life-hours:72}")
    private double decayHalfLifeHours;

    @Value("${app.ai.recommendation.weight.view:1.0}")
    private double weightView;

    @Value("${app.ai.recommendation.weight.search:1.5}")
    private double weightSearch;

    @Value("${app.ai.recommendation.weight.cart:3.0}")
    private double weightCart;

    @Value("${app.ai.recommendation.weight.purchase:5.0}")
    private double weightPurchase;

    /**
     * Gợi ý sản phẩm cho người dùng theo session
     */
    public List<ScoredProduct> recommendForSession(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return getPopularColdStartRecommendations(limit);
        }

        // Lấy tối đa 20 sự kiện gần nhất trong 14 ngày
        LocalDateTime cutoff = LocalDateTime.now().minusDays(14);
        List<UserEvent> events = trackingService.getEventsAfter(sessionId, cutoff);
        if (events == null || events.isEmpty()) {
            return getPopularColdStartRecommendations(limit);
        }

        // 1. Tính toán User Interest Profile Vector
        float[] userProfileVector = new float[vectorDimension];
        LocalDateTime now = LocalDateTime.now();
        int validEventCount = 0;
        String latestInterestName = "";

        for (UserEvent event : events) {
            float[] itemVector = null;

            if (event.getEventType() == EventType.SEARCH && event.getKeyword() != null) {
                // Vector hóa từ khóa tìm kiếm
                itemVector = embeddingService.embedText(event.getKeyword());
                if (latestInterestName.isEmpty()) latestInterestName = "từ khóa \"" + event.getKeyword() + "\"";
            } else if (event.getProductId() != null) {
                itemVector = vectorStoreService.getProductVector(event.getProductId());
                if (latestInterestName.isEmpty()) {
                    Product p = productRepository.findById(event.getProductId()).orElse(null);
                    if (p != null) latestInterestName = p.getName();
                }
            }

            if (itemVector == null) continue;

            // Tính trọng số hành vi
            double baseWeight = getEventWeight(event);

            // Tính phân rã theo thời gian (Recency Decay)
            long hoursAgo = Math.max(0, Duration.between(event.getCreatedAt(), now).toHours());
            double decay = Math.pow(0.5, (double) hoursAgo / decayHalfLifeHours);

            double finalWeight = baseWeight * decay;
            VectorMath.addWeighted(userProfileVector, itemVector, finalWeight);
            validEventCount++;
        }

        if (validEventCount == 0) {
            return getPopularColdStartRecommendations(limit);
        }

        // 2. Chuẩn hóa L2 Norm
        userProfileVector = VectorMath.normalize(userProfileVector);

        // 3. Loại bỏ sản phẩm đã mua hoặc đang có trong giỏ hàng
        Set<Long> excluded = trackingService.getExcludedProductIds(sessionId);

        // 4. Tìm kiếm trong Vector Database
        String reason = latestInterestName.isEmpty() ?
                "Gợi ý phù hợp với xu hướng xem của bạn" :
                "Dựa trên sự quan tâm của bạn tới " + latestInterestName;

        List<ScoredProduct> recommendations = vectorStoreService.searchByVector(
                userProfileVector, limit, 0.20, excluded, reason
        );

        if (recommendations.isEmpty()) {
            return getPopularColdStartRecommendations(limit);
        }
        return recommendations;
    }

    /**
     * Gợi ý sản phẩm tương tự khi người dùng đang xem trang chi tiết (Item-to-Item Similarity)
     */
    public List<ScoredProduct> recommendSimilarProducts(Long currentProductId, int limit) {
        float[] productVector = vectorStoreService.getProductVector(currentProductId);
        if (productVector == null) return Collections.emptyList();

        Product current = productRepository.findById(currentProductId).orElse(null);
        String reason = current != null ? "Sản phẩm tương tự với " + current.getName() : "Sản phẩm tương tự";

        return vectorStoreService.searchByVector(
                productVector, limit, 0.30, Set.of(currentProductId), reason
        );
    }

    private double getEventWeight(UserEvent event) {
        switch (event.getEventType()) {
            case VIEW -> {
                // Tăng nhẹ trọng số nếu thời gian dừng xem (dwell time) cao
                double dwellBonus = 0.0;
                if (event.getDurationSeconds() != null && event.getDurationSeconds() > 10) {
                    dwellBonus = Math.min(1.0, (event.getDurationSeconds() - 10) / 50.0);
                }
                return weightView + dwellBonus;
            }
            case SEARCH -> { return weightSearch; }
            case ADD_TO_CART -> { return weightCart; }
            case PURCHASE -> { return weightPurchase; }
            default -> { return 1.0; }
        }
    }

    /**
     * Chiến lược Cold-Start: Gợi ý các sản phẩm tiêu biểu đa dạng khi chưa có dữ liệu hành vi
     */
    private List<ScoredProduct> getPopularColdStartRecommendations(int limit) {
        return productRepository.findAll(PageRequest.of(0, limit))
                .getContent()
                .stream()
                .map(p -> new ScoredProduct(p, 0.85, "Sản phẩm nổi bật được nhiều người quan tâm"))
                .collect(Collectors.toList());
    }
}
