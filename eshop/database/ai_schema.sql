-- =====================================================================
-- eShop AI Extension Schema: User Tracking & Vector Database
-- =====================================================================
USE eshop;

-- 1. Bảng lưu trữ vết hành vi người dùng (User Tracking / Events)
CREATE TABLE IF NOT EXISTS user_event (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       VARCHAR(100) NOT NULL,               -- Định danh phiên duyệt web (Guest / Logged-in)
    user_id          BIGINT NULL,                         -- ID người dùng nếu có
    event_type       VARCHAR(30)  NOT NULL,               -- VIEW, SEARCH, ADD_TO_CART, PURCHASE
    product_id       BIGINT NULL,                         -- ID sản phẩm tương tác
    category_id      BIGINT NULL,                         -- ID danh mục sản phẩm
    keyword          VARCHAR(255) NULL,                   -- Từ khóa tìm kiếm (nếu event_type = SEARCH)
    duration_seconds INT DEFAULT 0,                       -- Thời gian dừng lại xem trang (Dwell Time)
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tương tác (dùng tính Recency Decay)
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Thời điểm cập nhật
    INDEX idx_ue_session (session_id),
    INDEX idx_ue_event (event_type),
    INDEX idx_ue_product (product_id),
    INDEX idx_ue_created (created_at)
) ENGINE = InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. Bảng lưu trữ Vector Embedding của sản phẩm (Vector Database)
CREATE TABLE IF NOT EXISTS product_vector (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id     BIGINT NOT NULL UNIQUE,                -- Khóa ngoại logic tới product
    document_text  TEXT NOT NULL,                         -- Văn bản tóm tắt sản phẩm được embedding
    embedding_json MEDIUMTEXT NOT NULL,                   -- Chuỗi JSON biểu diễn vector số thực (ví dụ float[384])
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pv_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
