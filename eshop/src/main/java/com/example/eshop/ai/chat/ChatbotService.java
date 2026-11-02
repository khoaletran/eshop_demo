package com.example.eshop.ai.chat;

import com.example.eshop.ai.model.ChatDto;
import com.example.eshop.ai.model.ScoredProduct;
import com.example.eshop.ai.tracking.UserTrackingService;
import com.example.eshop.ai.vector.VectorStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Chatbot RAG (Retrieval-Augmented Generation):
 * 1. Retrieval: Tìm sản phẩm phù hợp nhất trong kho hàng qua Vector Database.
 * 2. Augmentation: Đưa danh sách sản phẩm tìm được vào Prompt ngữ cảnh.
 * 3. Generation: Gọi Gemini LLM tạo câu trả lời tư vấn cho khách hàng.
 */
@Slf4j
@Service
public class ChatbotService {

    private final VectorStoreService vectorStoreService;
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
                          UserTrackingService trackingService) {
        this.vectorStoreService = vectorStoreService;
        this.trackingService = trackingService;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    public ChatDto.ChatResponse chat(String userMessage, String sessionId) {
        if (userMessage == null || userMessage.isBlank()) {
            return new ChatDto.ChatResponse(
                    "Dạ, em có thể hỗ trợ gì cho anh/chị về các sản phẩm công nghệ tại eShop ạ?",
                    Collections.emptyList(),
                    List.of("Tư vấn điện thoại tầm 20 triệu", "Tai nghe bluetooth chống ồn", "Laptop văn phòng")
            );
        }

        // 1. Ghi nhận tìm kiếm vào User Tracking
        if (sessionId != null && !sessionId.isBlank()) {
            trackingService.trackSearch(sessionId, userMessage, null);
        }

        // 2. RETRIEVAL: Tìm sản phẩm phù hợp trong Vector Database
        List<ScoredProduct> matchedProducts = vectorStoreService.searchByText(userMessage, 4, 0.05);

        // Chuẩn bị danh sách Card sản phẩm trả về giao diện
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

        // 3. AUGMENTATION & GENERATION: Gọi Gemini LLM với Context từ kho hàng
        String replyText = null;
        if (geminiApiKey != null && !geminiApiKey.isBlank() && !geminiApiKey.equals("demo_key") && geminiApiKey.startsWith("AIzaSy")) {
            replyText = callGeminiGenerateApi(userMessage, matchedProducts);
        }

        if (replyText == null || replyText.isBlank()) {
            // Câu trả lời mẫu đơn giản dựa trên RAG context
            StringBuilder sb = new StringBuilder("Dạ, eShop xin gợi ý các sản phẩm phù hợp nhất trong kho hàng cho anh/chị:\n");
            for (ScoredProduct sp : matchedProducts) {
                sb.append(String.format("- %s (Giá: %s VNĐ)\n", sp.getName(),
                        sp.getPrice() != null ? nf.format(sp.getPrice()) : "Liên hệ"));
            }
            sb.append("\nAnh/chị có thể xem chi tiết ở các thẻ sản phẩm bên dưới ạ!");
            replyText = sb.toString();
        }

        List<String> followUps = List.of(
                "Có được bảo hành chính hãng không?",
                "Chính sách giao hàng như thế nào?",
                "Có sản phẩm nào giá tốt hơn không?"
        );

        return new ChatDto.ChatResponse(replyText, productSummaries, followUps);
    }

    /**
     * Gọi Google Gemini Generate Content API với Context RAG
     */
    private String callGeminiGenerateApi(String userMessage, List<ScoredProduct> products) {
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("THÔNG TIN SẢN PHẨM CÓ TRONG KHO HÀNG:\n");
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

        String prompt = String.format("""
                Bạn là 'eShop Assistant' - Nhân viên tư vấn bán hàng công nghệ chuyên nghiệp, nhiệt tình và súc tích.
                Dựa vào thông tin các sản phẩm trong kho dưới đây:
                %s
                
                Hãy trả lời câu hỏi của khách hàng: "%s"
                Yêu cầu:
                - Tư vấn rõ ưu điểm và mức giá phù hợp.
                - Trả lời ngắn gọn, định dạng gạch đầu dòng rõ ràng.
                """, contextBuilder, userMessage);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "maxOutputTokens", maxTokens
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        List<String> candidateModels = List.of(geminiModel, "gemini-1.5-flash", "gemini-2.5-flash", "gemini-flash-latest");

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

        return null;
    }
}
