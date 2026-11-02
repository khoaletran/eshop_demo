package com.example.eshop.ai.chat;

import com.example.eshop.ai.model.ChatDto;
import com.example.eshop.ai.model.ScoredProduct;
import com.example.eshop.ai.tracking.UserTrackingService;
import com.example.eshop.ai.vector.EmbeddingService;
import com.example.eshop.ai.vector.VectorStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dịch vụ Chatbot hỏi đáp thông minh kết hợp RAG (Retrieval-Augmented Generation):
 * 1. Nhận câu hỏi từ khách hàng.
 * 2. Truy vấn Vector Database để trích xuất sản phẩm liên quan (Context Retrieval).
 * 3. Tạo Prompt tăng cường chứa thông tin sản phẩm thực tế trong kho hàng.
 * 4. Gọi LLM (Google Gemini API / Smart Advisor Fallback) để sinh câu trả lời tự nhiên, thân thiện.
 */
@Slf4j
@Service
public class ChatbotService {

    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final UserTrackingService trackingService;

    @Value("${app.ai.gemini.api-key:demo_key}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${app.ai.gemini.temperature:0.7}")
    private double temperature;

    @Value("${app.ai.gemini.max-output-tokens:1024}")
    private int maxTokens;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatbotService(VectorStoreService vectorStoreService,
                          EmbeddingService embeddingService,
                          UserTrackingService trackingService) {
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.trackingService = trackingService;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    public ChatDto.ChatResponse chat(String userMessage, String sessionId) {
        if (userMessage == null || userMessage.isBlank()) {
            return new ChatDto.ChatResponse("Dạ, em có thể hỗ trợ gì cho anh/chị về các sản phẩm công nghệ tại eShop ạ?",
                    Collections.emptyList(),
                    List.of("Tư vấn điện thoại chụp ảnh đẹp", "Tai nghe bluetooth dưới 2 triệu", "Laptop học tập văn phòng"));
        }

        // 1. Ghi nhận hành vi tìm kiếm qua chat vào User Tracking
        if (sessionId != null && !sessionId.isBlank()) {
            trackingService.trackSearch(sessionId, userMessage, null);
        }

        // 2. RETRIEVAL: Tìm kiếm ngữ nghĩa trong Vector Database
        List<ScoredProduct> matchedProducts = vectorStoreService.searchByText(userMessage, 4, 0.18);

        // 3. Chuẩn bị danh sách ProductSummary trả về cho giao diện hiển thị card
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        List<ChatDto.ProductSummary> productSummaries = matchedProducts.stream().map(sp -> {
            String priceStr = sp.getPrice() != null ? nf.format(sp.getPrice()) + " ₫" : "Liên hệ";
            return new ChatDto.ProductSummary(
                    sp.getId(),
                    sp.getName(),
                    priceStr,
                    sp.getImageUrl(),
                    sp.getCategoryName(),
                    Math.round(sp.getSimilarityScore() * 100.0) / 100.0
            );
        }).collect(Collectors.toList());

        // 4. GENERATION: Gọi LLM (Gemini hoặc Smart Advisor)
        String replyText;
        if (embeddingService.isRealApiKey(geminiApiKey)) {
            try {
                replyText = callGeminiGenerateApi(userMessage, matchedProducts);
            } catch (Exception e) {
                log.warn("Gọi Gemini LLM thất bại ({}), chuyển sang Smart Advisor.", e.getMessage());
                replyText = generateSmartFallbackReply(userMessage, matchedProducts);
            }
        } else {
            replyText = generateSmartFallbackReply(userMessage, matchedProducts);
        }

        // 5. Gợi ý các câu hỏi tiếp theo (Quick Follow-up Suggestions)
        List<String> followUps = generateFollowUps(userMessage, matchedProducts);

        return new ChatDto.ChatResponse(replyText, productSummaries, followUps);
    }

    /**
     * Gọi Google Gemini Generate Content API với Context RAG
     */
    private String callGeminiGenerateApi(String userMessage, List<ScoredProduct> products) {
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("THÔNG TIN SẢN PHẨM CÓ TRONG KHO CỬA HÀNG:\n");
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        for (int i = 0; i < products.size(); i++) {
            ScoredProduct p = products.get(i);
            contextBuilder.append(String.format("%d. %s | Giá: %s VNĐ | Danh mục: %s | Tồn kho: %d | Đặc điểm: %s\n",
                    i + 1, p.getName(),
                    p.getPrice() != null ? nf.format(p.getPrice()) : "0",
                    p.getCategoryName(),
                    p.getProduct().getQuantity(),
                    p.getDescription()));
        }

        String systemInstruction = """
                Bạn là 'eShop AI Assistant' - Chuyên viên tư vấn bán hàng công nghệ chuyên nghiệp, nhiệt tình, lịch sự.
                Nhiệm vụ: Tư vấn cho khách hàng dựa trên dữ liệu sản phẩm trong kho được cung cấp ở trên.
                Quy tắc:
                - Tư vấn rõ ưu điểm nổi bật và mức giá phù hợp nhu cầu của khách.
                - Nếu khách hỏi sản phẩm không có trong kho, hãy giải thích khéo léo và gợi ý các sản phẩm tương tự đang có.
                - Giữ câu trả lời súc tích, tự nhiên, định dạng danh sách gạch đầu dòng rõ ràng.
                """;

        String prompt = systemInstruction + "\n\n" + contextBuilder + "\nCÂU HỎI CỦA KHÁCH HÀNG: " + userMessage;

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(textPart));
        Map<String, Object> body = Map.of(
                "contents", List.of(content),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "maxOutputTokens", maxTokens
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // Danh sách model ưu tiên từ mới đến cũ
        List<String> candidateModels = new ArrayList<>();
        if (geminiModel != null && !geminiModel.isBlank()) {
            candidateModels.add(geminiModel);
        }
        candidateModels.addAll(List.of("gemini-1.5-flash", "gemini-2.5-flash", "gemini-flash-latest", "gemini-1.5-pro"));

        for (String modelName : candidateModels) {
            try {
                String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                        modelName, geminiApiKey);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                        return textNode.asText();
                    }
                }
            } catch (Exception e) {
                log.debug("Thử model generate {} thất bại: {}", modelName, e.getMessage());
            }
        }
        return generateSmartFallbackReply(userMessage, products);
    }

    /**
     * Smart Generative Advisor Fallback (Hoạt động offline hoặc khi chưa có API key):
     * Phân tích câu hỏi, so khớp thông số từ kho hàng và tạo câu trả lời mạch lạc, chuẩn nghiệp vụ.
     */
    private String generateSmartFallbackReply(String userMessage, List<ScoredProduct> products) {
        if (products.isEmpty()) {
            return "Dạ eShop hiện chưa tìm thấy sản phẩm hoàn toàn khớp với yêu cầu \"" + userMessage +
                   "\" của anh/chị. Anh/chị có thể tham khảo thêm các danh mục Điện thoại, Laptop, Tai nghe hoặc Màn hình ở thanh menu giúp em nhé!";
        }

        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        StringBuilder sb = new StringBuilder();
        sb.append("Dạ chào anh/chị! Dựa trên nhu cầu của anh/chị, eShop xin gợi ý các sản phẩm phù hợp nhất trong kho hàng:\n\n");

        for (int i = 0; i < products.size(); i++) {
            ScoredProduct sp = products.get(i);
            String priceStr = sp.getPrice() != null ? nf.format(sp.getPrice()) + " ₫" : "Liên hệ";
            sb.append(String.format("🔹 **%s** — Giá: **%s**\n", sp.getName(), priceStr));
            sb.append(String.format("   - Danh mục: *%s* | Tồn kho: %d sản phẩm\n", sp.getCategoryName(), sp.getProduct().getQuantity()));
            if (sp.getDescription() != null && !sp.getDescription().isBlank()) {
                sb.append(String.format("   - Điểm nổi bật: %s\n", sp.getDescription()));
            }
            sb.append("\n");
        }

        sb.append("💡 Anh/chị có thể bấm trực tiếp vào thẻ sản phẩm bên dưới để xem chi tiết hoặc thêm ngay vào giỏ hàng ạ!");
        return sb.toString();
    }

    private List<String> generateFollowUps(String userMessage, List<ScoredProduct> products) {
        List<String> list = new ArrayList<>();
        if (!products.isEmpty()) {
            list.add("So sánh chi tiết các sản phẩm trên");
            list.add("Sản phẩm nào có pin trâu nhất?");
            list.add("Chính sách bảo hành và giao hàng thế nào?");
        } else {
            list.add("Xem danh sách điện thoại bán chạy");
            list.add("Tư vấn laptop sinh viên văn phòng");
            list.add("Phụ kiện sạc nhanh chính hãng");
        }
        return list;
    }
}
