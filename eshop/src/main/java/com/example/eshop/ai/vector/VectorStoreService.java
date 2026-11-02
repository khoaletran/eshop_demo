package com.example.eshop.ai.vector;

import com.example.eshop.ai.model.ProductVector;
import com.example.eshop.ai.model.ScoredProduct;
import com.example.eshop.ai.repository.ProductVectorRepository;
import com.example.eshop.entity.Product;
import com.example.eshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dịch vụ Vector Database lưu trữ & truy vấn vector tương đồng (Vector Store Service):
 * - Đồng bộ vector nhúng vào cơ sở dữ liệu bảng product_vector.
 * - Duy trì bộ nhớ đệm (In-memory Vector Cache) cho tốc độ tìm kiếm cực nhanh (dưới 1ms).
 * - Tự động index 50 sản phẩm khi khởi động ứng dụng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductVectorRepository productVectorRepository;
    private final EmbeddingService embeddingService;

    // Cache vector in-memory: Key = ProductId, Value = float[] embedding
    private final Map<Long, float[]> vectorCache = new ConcurrentHashMap<>();
    private final Map<Long, Product> productCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=== Đang khởi động Vector Database & Lập chỉ mục sản phẩm ===");
        reindexAll();
        log.info("=== Hoàn tất lập chỉ mục Vector Database: {} sản phẩm đã sẵn sàng ===", vectorCache.size());
    }

    /**
     * Lập chỉ mục lại toàn bộ sản phẩm trong hệ thống
     */
    @Transactional
    public synchronized void reindexAll() {
        List<Product> products = productRepository.findAllWithCategory();
        for (Product p : products) {
            indexProductInternal(p);
        }
    }

    /**
     * Lập chỉ mục một sản phẩm cụ thể
     */
    @Transactional
    public void indexProduct(Product product) {
        indexProductInternal(product);
    }

    private void indexProductInternal(Product p) {
        String docText = buildProductDocument(p);
        float[] embedding = embeddingService.embedText(docText);
        String json = embeddingService.toJson(embedding);

        ProductVector pv = productVectorRepository.findByProductId(p.getId())
                .orElse(new ProductVector());
        pv.setProduct(p);
        pv.setDocumentText(docText);
        pv.setEmbeddingJson(json);
        productVectorRepository.save(pv);

        vectorCache.put(p.getId(), embedding);
        productCache.put(p.getId(), p);
    }

    /**
     * Xây dựng đoạn văn bản ngữ nghĩa đại diện cho sản phẩm (Semantic Document)
     */
    public String buildProductDocument(Product p) {
        String catName = "";
        try {
            if (p.getCategory() != null) {
                catName = p.getCategory().getName();
            }
        } catch (Exception ignored) {}
        return String.format("Sản phẩm: %s. Danh mục: %s. Giá bán: %s VNĐ. Mô tả: %s",
                p.getName(),
                catName,
                p.getPrice() != null ? p.getPrice().toPlainString() : "0",
                p.getDescription() != null ? p.getDescription() : "");
    }

    /**
     * Lấy vector của một sản phẩm
     */
    public float[] getProductVector(Long productId) {
        float[] vec = vectorCache.get(productId);
        if (vec == null) {
            productVectorRepository.findByProductId(productId).ifPresent(pv -> {
                float[] loaded = embeddingService.fromJson(pv.getEmbeddingJson());
                vectorCache.put(productId, loaded);
                productCache.put(productId, pv.getProduct());
            });
            vec = vectorCache.get(productId);
        }
        return vec;
    }

    /**
     * Tìm kiếm tương đồng ngữ nghĩa bằng câu truy vấn văn bản (Text Query -> Vector Search)
     */
    public List<ScoredProduct> searchByText(String queryText, int topK, double threshold) {
        float[] queryVector = embeddingService.embedText(queryText);
        return searchByVector(queryVector, topK, threshold, Collections.emptySet(), "Phù hợp với tìm kiếm: " + queryText);
    }

    /**
     * Tìm kiếm sản phẩm tương đồng với một Vector đặc trưng (User Profile Vector / Item Vector)
     */
    public List<ScoredProduct> searchByVector(float[] targetVector, int topK, double threshold,
                                             Collection<Long> excludedProductIds, String defaultReason) {
        if (targetVector == null || targetVector.length == 0 || vectorCache.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredProduct> scoredList = new ArrayList<>();
        Set<Long> excluded = excludedProductIds != null ? new HashSet<>(excludedProductIds) : Collections.emptySet();

        for (Map.Entry<Long, float[]> entry : vectorCache.entrySet()) {
            Long productId = entry.getKey();
            if (excluded.contains(productId)) {
                continue;
            }

            Product product = productCache.computeIfAbsent(productId, id -> productRepository.findById(id).orElse(null));
            if (product == null) continue;

            double score = VectorMath.cosineSimilarity(targetVector, entry.getValue());
            if (score >= threshold) {
                scoredList.add(new ScoredProduct(product, score, defaultReason));
            }
        }

        // Sắp xếp theo điểm tương đồng giảm dần (Cao nhất lên đầu)
        scoredList.sort((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()));

        if (scoredList.size() > topK) {
            return scoredList.subList(0, topK);
        }
        return scoredList;
    }

    public int getIndexedCount() {
        return vectorCache.size();
    }
}
