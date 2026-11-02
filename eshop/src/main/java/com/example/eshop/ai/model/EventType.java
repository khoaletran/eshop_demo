package com.example.eshop.ai.model;

/**
 * Phân loại loại hành vi người dùng (User Interaction Events).
 * Mỗi loại hành vi đại diện cho một mức độ quan tâm (Intent Level) khác nhau.
 */
public enum EventType {
    VIEW,        // Xem chi tiết sản phẩm (Trọng số mặc định: 1.0)
    SEARCH,      // Tìm kiếm từ khóa (Trọng số mặc định: 1.5)
    ADD_TO_CART, // Thêm sản phẩm vào giỏ hàng (Trọng số mặc định: 3.0)
    PURCHASE     // Hoàn tất mua sản phẩm (Trọng số mặc định: 5.0)
}
