package com.example.eshop.ai.vector;

/**
 * Các thuật toán tính toán vector toán học:
 * - Cosine Similarity: Đo lường góc giữa 2 vector đặc trưng trong không gian N-chiều.
 * - Chuẩn hóa vector L2 (Unit Vector Norm).
 * - Phép cộng vector có trọng số phục vụ tổng hợp User Interest Profile Vector.
 */
public final class VectorMath {

    private VectorMath() {}

    /**
     * Tính độ tương đồng Cosine giữa hai vector:
     * CosineSimilarity(A, B) = (A . B) / (||A|| * ||B||)
     * Kết quả nằm trong khoảng [-1.0, 1.0], chuẩn hóa về [0.0, 1.0].
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA <= 1e-9 || normB <= 1e-9) {
            return 0.0;
        }

        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        // Đưa về khoảng [0, 1] cho dễ hiểu
        return Math.max(0.0, Math.min(1.0, (similarity + 1.0) / 2.0));
    }

    /**
     * Chuẩn hóa vector về độ dài đơn vị (Unit Vector L2 Norm)
     */
    public static float[] normalize(float[] v) {
        if (v == null || v.length == 0) return v;
        double norm = 0.0;
        for (float val : v) {
            norm += (double) val * val;
        }
        norm = Math.sqrt(norm);
        if (norm <= 1e-9) return v;

        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            result[i] = (float) (v[i] / norm);
        }
        return result;
    }

    /**
     * Cộng dồn vector có trọng số: target += addition * weight
     */
    public static void addWeighted(float[] target, float[] addition, double weight) {
        if (target == null || addition == null || target.length != addition.length) return;
        for (int i = 0; i < target.length; i++) {
            target[i] += (float) (addition[i] * weight);
        }
    }
}
