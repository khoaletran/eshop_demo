package com.example.eshop.ai.vector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Dịch vụ tạo Vector Embeddings từ văn bản:
 * - Gọi Google Gemini Embedding API khi có API key.
 * - Tự động dùng Bag-of-Words Vector Hashing đơn giản khi chạy thử nghiệm local.
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${app.ai.gemini.api-key:demo_key}")
    private String geminiApiKey;

    @Value("${app.ai.vector.dimension:384}")
    private int vectorDimension;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public EmbeddingService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Tạo vector embedding từ văn bản
     */
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            return new float[vectorDimension];
        }

        // 1. Khi có API Key thật: Gọi Google Gemini Embedding API
        if (geminiApiKey != null && !geminiApiKey.isBlank() && !geminiApiKey.equals("demo_key") && geminiApiKey.startsWith("AIzaSy")) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=" + geminiApiKey;

                Map<String, Object> body = Map.of(
                        "content", Map.of("parts", List.of(Map.of("text", text))),
                        "outputDimensionality", vectorDimension
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode valuesNode = root.path("embedding").path("values");
                    if (valuesNode.isArray() && valuesNode.size() > 0) {
                        float[] vec = new float[valuesNode.size()];
                        for (int i = 0; i < valuesNode.size(); i++) {
                            vec[i] = (float) valuesNode.get(i).asDouble();
                        }
                        return VectorMath.normalize(vec);
                    }
                }
            } catch (Exception e) {
                log.debug("Gọi Gemini Embedding không thành công, dùng Bag-of-Words Vectorizer: {}", e.getMessage());
            }
        }

        // 2. Chế độ đơn giản: Bag-of-Words Vector Hashing chuẩn hóa L2
        float[] vector = new float[vectorDimension];
        String[] words = text.toLowerCase().split("[\\s,;:.!?-]+");
        for (String w : words) {
            if (!w.isBlank()) {
                int index = Math.abs(w.hashCode()) % vectorDimension;
                vector[index] += 1.0f;
            }
        }
        return VectorMath.normalize(vector);
    }

    public String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            return "[]";
        }
    }

    public float[] fromJson(String json) {
        if (json == null || json.isBlank()) return new float[vectorDimension];
        try {
            List<Double> list = objectMapper.readValue(json, new TypeReference<List<Double>>() {});
            float[] vec = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                vec[i] = list.get(i).floatValue();
            }
            return vec;
        } catch (Exception e) {
            return new float[vectorDimension];
        }
    }
}
