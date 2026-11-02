package com.example.eshop;

import com.example.eshop.ai.chat.ChatbotService;
import com.example.eshop.ai.model.ChatDto;
import com.example.eshop.ai.model.EventType;
import com.example.eshop.ai.model.ScoredProduct;
import com.example.eshop.ai.model.UserEvent;
import com.example.eshop.ai.recommendation.ProductRecommendationService;
import com.example.eshop.ai.repository.ProductVectorRepository;
import com.example.eshop.ai.repository.UserEventRepository;
import com.example.eshop.ai.tracking.UserTrackingService;
import com.example.eshop.ai.vector.VectorStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class EshopAiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserTrackingService trackingService;

    @Autowired
    private UserEventRepository userEventRepository;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private ProductVectorRepository productVectorRepository;

    @Autowired
    private ProductRecommendationService recommendationService;

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private ObjectMapper objectMapper;

    // ==========================================
    // 1. KIỂM THỬ PHẦN 1: USER TRACKING
    // ==========================================

    @Test
    @DisplayName("AI Part 1: Ghi nhận sự kiện VIEW, SEARCH, ADD_TO_CART vào User Tracking")
    void testUserTrackingEvents() {
        String testSession = "test_session_" + System.currentTimeMillis();

        // 1. Track VIEW
        UserEvent viewEvent = trackingService.trackView(testSession, 1L, 1L, 15);
        assertThat(viewEvent).isNotNull();
        assertThat(viewEvent.getEventType()).isEqualTo(EventType.VIEW);
        assertThat(viewEvent.getProductId()).isEqualTo(1L);
        assertThat(viewEvent.getCreatedAt()).isNotNull();
        assertThat(viewEvent.getUpdatedAt()).isNotNull();

        // 2. Track SEARCH
        UserEvent searchEvent = trackingService.trackSearch(testSession, "tai nghe không dây", 5L);
        assertThat(searchEvent).isNotNull();
        assertThat(searchEvent.getEventType()).isEqualTo(EventType.SEARCH);
        assertThat(searchEvent.getKeyword()).isEqualTo("tai nghe không dây");

        // 3. Track ADD_TO_CART
        UserEvent cartEvent = trackingService.trackAddToCart(testSession, 2L, 1L);
        assertThat(cartEvent).isNotNull();
        assertThat(cartEvent.getEventType()).isEqualTo(EventType.ADD_TO_CART);

        // Kiểm tra lưu vết trong DB
        List<UserEvent> events = trackingService.getRecentEvents(testSession, 10);
        assertThat(events).hasSize(3);
    }

    @Test
    @DisplayName("AI Part 1: Endpoint POST /api/tracking/event nhận dữ liệu từ Client")
    void testApiTrackingEventEndpoint() throws Exception {
        String payload = """
                {
                    "eventType": "VIEW",
                    "productId": 5,
                    "categoryId": 2,
                    "durationSeconds": 25
                }
                """;

        mockMvc.perform(post("/api/tracking/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.sessionId").isNotEmpty());
    }

    @Test
    @DisplayName("AI Part 1: Dashboard quản trị /admin/tracking hoạt động")
    void testTrackingDashboard() throws Exception {
        mockMvc.perform(get("/admin/tracking"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tracking/index"))
                .andExpect(model().attributeExists("stats", "recentEvents"));
    }

    // ==========================================
    // 2. KIỂM THỬ PHẦN 2: VECTOR DATABASE
    // ==========================================

    @Test
    @DisplayName("AI Part 2: Vector Database đã lập chỉ mục toàn bộ sản phẩm")
    void testVectorDatabaseIndexing() {
        assertThat(vectorStoreService.getIndexedCount()).isGreaterThanOrEqualTo(50);
        assertThat(productVectorRepository.count()).isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("AI Part 2: Tìm kiếm tương đồng vector (Cosine Similarity) với từ khóa 'chống ồn'")
    void testVectorSimilaritySearch() {
        List<ScoredProduct> results = vectorStoreService.searchByText("tai nghe chống ồn", 5, 0.20);
        assertThat(results).isNotEmpty();

        // Sản phẩm đầu bảng phải là tai nghe chống ồn (Sony WH-1000XM5 hoặc AirPods Pro hoặc Soundcore)
        String topProductName = results.get(0).getName();
        assertThat(topProductName.toLowerCase()).containsAnyOf("airpods", "sony", "jbl", "soundcore");
        assertThat(results.get(0).getSimilarityScore()).isGreaterThan(0.3);
    }

    @Test
    @DisplayName("AI Part 2: Gợi ý sản phẩm dựa trên hành vi người dùng (User Profile Vector + Recency Decay)")
    void testRecommendationBasedOnUserTracking() {
        String testSession = "rec_session_" + System.currentTimeMillis();

        // Người dùng tương tác nhiều với nhóm Laptop (MacBook M3 id=8, Dell id=9)
        trackingService.trackView(testSession, 8L, 3L, 45);
        trackingService.trackSearch(testSession, "laptop văn phòng", 3L);
        trackingService.trackAddToCart(testSession, 9L, 3L);

        List<ScoredProduct> recs = recommendationService.recommendForSession(testSession, 4);
        assertThat(recs).isNotEmpty();

        // AI phải nhận diện sở thích và gợi ý các sản phẩm liên quan đến Laptop hoặc phụ kiện máy tính
        boolean hasRelatedProduct = recs.stream().anyMatch(r ->
                r.getCategoryName().equalsIgnoreCase("Laptop") ||
                r.getCategoryName().equalsIgnoreCase("Bàn phím") ||
                r.getCategoryName().equalsIgnoreCase("Chuột") ||
                r.getCategoryName().equalsIgnoreCase("Màn hình")
        );
        assertThat(hasRelatedProduct).isTrue();
    }

    // ==========================================
    // 3. KIỂM THỬ PHẦN 3: CHATBOT RAG HỎI ĐÁP
    // ==========================================

    @Test
    @DisplayName("AI Part 3: Chatbot RAG trả lời câu hỏi và đính kèm sản phẩm gợi ý")
    void testChatbotRagService() {
        ChatDto.ChatResponse res = chatbotService.chat("Tư vấn cho tôi điện thoại chụp ảnh đẹp tầm 20 triệu", "test_session_chat");
        assertThat(res).isNotNull();
        assertThat(res.getReply()).isNotEmpty();
        assertThat(res.getSuggestedProducts()).isNotEmpty();

        // Kiểm tra sản phẩm gợi ý thuộc danh mục Điện thoại hoặc chứa điện thoại thông minh
        boolean hasSmartPhone = res.getSuggestedProducts().stream().anyMatch(p ->
                "Điện thoại".equalsIgnoreCase(p.getCategory()) ||
                p.getName().contains("iPhone") || p.getName().contains("Galaxy") ||
                p.getName().contains("Redmi") || p.getName().contains("OPPO")
        );
        assertThat(hasSmartPhone).isTrue();
    }

    @Test
    @DisplayName("AI Part 3: Endpoint POST /api/ai/chat phản hồi JSON đầy đủ")
    void testApiAiChatEndpoint() throws Exception {
        ChatDto.ChatRequest req = new ChatDto.ChatRequest("Shop có bán loa bluetooth chống nước không?", "session_123");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.suggestedProducts").isArray());
    }

    @Test
    @DisplayName("AI Part 3: Trang giao diện Chatbot /chat tải thành công")
    void testChatPage() throws Exception {
        mockMvc.perform(get("/chat"))
                .andExpect(status().isOk())
                .andExpect(view().name("chat/index"))
                .andExpect(model().attributeExists("sessionId"));
    }
}
