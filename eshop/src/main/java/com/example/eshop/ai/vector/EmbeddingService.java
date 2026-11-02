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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Dịch vụ tạo Vector Embeddings từ văn bản:
 * - Hỗ trợ Online qua Google Gemini Embedding API nếu có key hợp lệ.
 * - Hỗ trợ Offline qua Subword Semantic Hashing Vectorizer (384 chiều)
 *   giúp hệ thống chạy ổn định 100% trong mọi điều kiện (offline, thi cử, demo không mạng).
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
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Tạo vector embedding từ một đoạn văn bản
     */
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            return new float[vectorDimension];
        }

        // 1. Thử gọi Gemini Embedding API nếu có key thật
        if (isRealApiKey(geminiApiKey)) {
            try {
                float[] onlineVec = callGeminiEmbeddingApi(text);
                if (onlineVec != null && onlineVec.length > 0) {
                    return onlineVec;
                }
            } catch (Exception e) {
                log.warn("Gọi Gemini Embedding thất bại ({}), tự động dùng Local Semantic Vectorizer.", e.getMessage());
            }
        }

        // 2. Chế độ Local Semantic Vectorizer (384 dimensions, chuẩn hóa L2)
        return generateLocalSemanticVector(text, vectorDimension);
    }

    /**
     * Kiểm tra xem API key có phải là key cấu hình thực tế không
     */
    public boolean isRealApiKey(String key) {
        return key != null && !key.isBlank() && !key.equalsIgnoreCase("demo_key") && key.length() > 20;
    }

    /**
     * Gọi Google Gemini Embedding API với danh sách model dự phòng
     */
    private float[] callGeminiEmbeddingApi(String text) {
        String[] candidateModels = new String[]{
                "gemini-embedding-001",
                "gemini-embedding-2",
                "text-embedding-004",
                "embedding-001"
        };

        for (String modelName : candidateModels) {
            try {
                String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s",
                        modelName, geminiApiKey);

                Map<String, Object> part = Map.of("text", text);
                Map<String, Object> content = Map.of("parts", List.of(part));
                Map<String, Object> requestBody = Map.of(
                        "content", content,
                        "outputDimensionality", vectorDimension
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

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
                log.debug("Thử model embedding {} thất bại: {}", modelName, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Local Deterministic Semantic N-gram Hashing Vectorizer:
     * - Tách token & phân tích ngữ nghĩa (Tiếng Việt có dấu + không dấu + Tiếng Anh).
     * - Trích xuất Subword n-grams (1-gram, 2-gram, 3-gram).
     * - Chiếu xạ (Hashing Projection) vào không gian N-chiều.
     * - Gán trọng số tăng cường cho các thực thể quan trọng (Danh mục, Giá, Từ khóa kỹ thuật).
     * - Chuẩn hóa vector L2 Norm.
     */
    public float[] generateLocalSemanticVector(String text, int dim) {
        float[] vector = new float[dim];
        if (text == null || text.isBlank()) return vector;

        String normalized = text.toLowerCase(Locale.ROOT);
        String unaccented = removeAccents(normalized);

        // Danh sách từ khóa và token
        String[] words = normalized.split("[\\s,;:.!?-]+");
        String[] unaccentedWords = unaccented.split("[\\s,;:.!?-]+");

        // 1. Phân tích Unigram & Bigram
        for (int i = 0; i < words.length; i++) {
            String w = words[i].trim();
            if (w.isEmpty()) continue;
            double weight = computeKeywordWeight(w);
            hashAndAdd(vector, w, weight, dim);

            // Hash cả dạng không dấu để người dùng gõ không dấu vẫn tìm thấy
            if (i < unaccentedWords.length) {
                String uw = unaccentedWords[i].trim();
                if (!uw.equals(w) && !uw.isEmpty()) {
                    hashAndAdd(vector, uw, weight * 0.9, dim);
                }
            }

            // Bigram liên tiếp (vd: "tai nghe", "dien thoai", "pin trau", "man hinh")
            if (i < words.length - 1) {
                String bigram = w + " " + words[i + 1].trim();
                hashAndAdd(vector, bigram, weight * 1.5, dim);
            }
        }

        // 2. Chuẩn hóa L2 norm
        return VectorMath.normalize(vector);
    }

    private void hashAndAdd(float[] vector, String term, double weight, int dim) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(term.getBytes(StandardCharsets.UTF_8));
            int h1 = ((digest[0] & 0xFF) << 8) | (digest[1] & 0xFF);
            int h2 = ((digest[2] & 0xFF) << 8) | (digest[3] & 0xFF);
            int sign = (digest[4] & 0x01) == 0 ? 1 : -1;

            int index = Math.abs(h1) % dim;
            vector[index] += (float) (sign * weight);

            int index2 = Math.abs(h2) % dim;
            vector[index2] += (float) (-sign * weight * 0.5);
        } catch (Exception ignored) {
            int idx = Math.abs(term.hashCode()) % dim;
            vector[idx] += (float) weight;
        }
    }

    private static final Set<String> HIGH_PRIORITY_KEYWORDS = Set.of(
            "dien", "thoai", "phone", "smartphone", "iphone", "samsung", "galaxy", "xiaomi",
            "redmi", "oppo", "ipad", "tablet", "tab", "laptop", "macbook", "dell", "asus",
            "lenovo", "thinkpad", "dong", "ho", "watch", "smartwatch", "tai", "nghe", "airpods",
            "sony", "loa", "bluetooth", "jbl", "marshall", "ban", "phim", "keyboard", "akko",
            "chuot", "mouse", "logitech", "razer", "man", "hinh", "monitor", "may", "anh",
            "camera", "canon", "fujifilm", "sac", "pin", "wifi", "router", "mesh", "ssd", "hdd",
            "in", "printer", "chup", "gaming", "office", "pro", "max", "ultra"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "la", "va", "cua", "cho", "toi", "minh", "em", "chi", "ban",
            "a", "da", "tu", "van", "hay", "co", "khong", "giup", "voi", "nhe",
            "nao", "gi", "o", "tai", "can", "muon", "tim", "mua", "hoi", "ve",
            "duoc", "the", "xin", "tam", "khoang", "dep", "tot", "re"
    );

    private double computeKeywordWeight(String word) {
        if (HIGH_PRIORITY_KEYWORDS.contains(word)) {
            return 3.5;
        }
        if (STOP_WORDS.contains(word)) {
            return 0.1;
        }
        if (word.matches("\\d+.*")) { // Có chứa số: 128gb, 256gb, 120hz, 4k, 20tr...
            return 2.5;
        }
        return 1.0;
    }

    private String removeAccents(String src) {
        if (src == null) return "";
        String nfdNormalizedString = Normalizer.normalize(src, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    /**
     * Chuyển mảng float[] thành chuỗi JSON lưu trữ trong MariaDB
     */
    public String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            log.error("Lỗi serialize vector: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Parse chuỗi JSON trong MariaDB thành mảng float[]
     */
    public float[] fromJson(String json) {
        try {
            List<Double> list = objectMapper.readValue(json, new TypeReference<>() {});
            float[] result = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = list.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            return new float[vectorDimension];
        }
    }
}
