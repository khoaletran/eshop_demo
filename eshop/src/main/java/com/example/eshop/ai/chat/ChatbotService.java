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
 * Chatbot RAG (Retrieval-Augmented Generation) kết nối Google Gemini API:
 * 1. Nhận diện câu chào hỏi để phản hồi tự nhiên, không ép hiện thẻ sản phẩm bừa bãi.
 * 2. Retrieval: Tìm sản phẩm phù hợp trong Vector Database khi khách có nhu cầu mua sắm.
 * 3. Augmentation: Đưa thông tin sản phẩm thực tế vào Prompt.
 * 4. Generation: Gọi Gemini LLM (gemini-flash-lite-latest / gemini-3.5-flash-lite) tạo câu trả lời chuyên nghiệp.
 */
@Slf4j
@Service
public class ChatbotService {

    private final VectorStoreService vectorStoreService;
    private final UserTrackingService trackingService;

    @Value("${app.ai.gemini.api-key:demo_key}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-flash-lite-latest}")
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
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    public ChatDto.ChatResponse chat(String userMessage, String sessionId) {
        if (userMessage == null || userMessage.isBlank()) {
            return new ChatDto.ChatResponse(
                    "Dạ, eShop xin chào! Em có thể hỗ trợ tư vấn sản phẩm công nghệ nào cho anh/chị hôm nay ạ?",
                    Collections.emptyList(),
                    List.of("Tư vấn điện thoại tầm 20 triệu", "Tai nghe bluetooth chống ồn", "Laptop văn phòng mỏng nhẹ")
            );
        }

        // 1. Kiểm tra nếu là câu chào hỏi thông thường
        boolean isGreeting = userMessage.trim().toLowerCase(Locale.ROOT)
                .matches("^(hi|hello|hey|alo|chào|xin chào|halo|chao shop|chào shop|chao ban|chào bạn|hello shop)[!.,? ]*$");

        List<ScoredProduct> matchedProducts = Collections.emptyList();
        List<ChatDto.ProductSummary> productSummaries = Collections.emptyList();

        // 2. Ghi nhận tìm kiếm vào User Tracking & Retrieval từ Vector DB (chỉ khi không phải câu chào đơn thuần)
        if (!isGreeting) {
            if (sessionId != null && !sessionId.isBlank()) {
                trackingService.trackSearch(sessionId, userMessage, null);
            }

            matchedProducts = vectorStoreService.searchByText(userMessage, 4, 0.05);

            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            productSummaries = matchedProducts.stream().map(sp -> {
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
        }

        // 3. Gọi Gemini LLM tạo câu trả lời
        String replyText = callGeminiGenerateApi(userMessage, matchedProducts, isGreeting);

        List<String> followUps = isGreeting ?
                List.of("Tư vấn điện thoại chụp ảnh đẹp", "Tai nghe chống ồn pin trâu", "Laptop học tập văn phòng") :
                List.of("Sản phẩm có sẵn hàng không?", "Chính sách bảo hành ra sao?", "Có chương trình khuyến mãi gì không?");

        return new ChatDto.ChatResponse(replyText, productSummaries, followUps);
    }

    /**
     * Gọi Google Gemini Generate Content API
     */
    private String callGeminiGenerateApi(String userMessage, List<ScoredProduct> products, boolean isGreeting) {
        String prompt;

        if (isGreeting) {
            prompt = String.format("""
                    Bạn là 'eShop Assistant' - Nhân viên tư vấn bán hàng công nghệ thân thiện, lịch sự và súc tích của cửa hàng eShop.
                    Khách hàng vừa gửi lời chào: "%s".
                    Hãy gửi lời chào lại khách một cách tự nhiên bằng tiếng Việt, giới thiệu bạn là Trợ lý eShop và hỏi xem khách hàng đang quan tâm đến dòng sản phẩm nào (điện thoại, laptop, tai nghe, phụ kiện...).
                    """, userMessage);
        } else {
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("THÔNG TIN SẢN PHẨM TRONG KHO CỬA HÀNG:\n");
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

            prompt = String.format("""
                    Bạn là 'eShop Assistant' - Chuyên viên tư vấn bán hàng công nghệ chuyên nghiệp, nhiệt tình và súc tích.
                    Dựa vào thông tin các sản phẩm trong kho dưới đây:
                    %s
                    
                    Hãy tư vấn giải đáp câu hỏi của khách hàng: "%s"
                    Yêu cầu:
                    - Tư vấn đúng trọng tâm nhu cầu của khách hàng, nêu bật ưu điểm của sản phẩm trong kho.
                    - Báo giá rõ ràng, trung thực.
                    - Giữ câu trả lời súc tích, định dạng gạch đầu dòng rõ ràng.
                    """, contextBuilder, userMessage);
        }

        // Gọi Google Gemini API qua danh sách model hoạt động
        List<String> candidateModels = List.of(
                geminiModel,
                "gemini-flash-lite-latest",
                "gemini-3.5-flash-lite",
                "gemma-4-26b-a4b-it",
                "gemini-2.5-flash",
                "gemini-1.5-flash"
        );

        if (geminiApiKey != null && !geminiApiKey.isBlank() && !geminiApiKey.equals("demo_key")) {
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

            for (String modelName : candidateModels) {
                try {
                    String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                            modelName, geminiApiKey);
                    ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JsonNode root = objectMapper.readTree(response.getBody());
                        JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                        if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                            return textNode.asText().trim();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Thử model generate {} thất bại: {}", modelName, e.getMessage());
                }
            }
        }

        // Phản hồi mẫu dự phòng nếu chưa có API key
        if (isGreeting) {
            return "Dạ, eShop xin chào anh/chị! Em có thể hỗ trợ anh/chị tìm kiếm sản phẩm công nghệ nào hôm nay ạ?";
        }

        StringBuilder sb = new StringBuilder("Dạ, eShop xin gợi ý các sản phẩm phù hợp nhất trong kho hàng cho anh/chị:\n");
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        for (ScoredProduct sp : products) {
            sb.append(String.format("- %s (Giá: %s VNĐ)\n", sp.getName(),
                    sp.getPrice() != null ? nf.format(sp.getPrice()) : "Liên hệ"));
        }
        sb.append("\nAnh/chị có thể xem chi tiết ở các thẻ sản phẩm bên dưới ạ!");
        return sb.toString();
    }
}
