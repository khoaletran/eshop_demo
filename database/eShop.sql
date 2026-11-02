-- =====================================================================
--  eShop.sql  –  CSDL mẫu cho dự án Shopping Cart (Spring Boot + JPA)
--  MariaDB 10.6+ / 11.x      Charset: utf8mb4
--
--  Phần A (bắt buộc - dùng từ Phần 1): category (15 dòng), product (50 dòng)
--  Phần B (dùng ở Phần 3 – Security):   users (tài khoản đăng nhập)
--  Phần C (dùng ở Phần 2 – Giỏ hàng):   orders, order_detail (đơn hàng)
--
--  Tài khoản mẫu (mật khẩu đều là 123456, đã băm BCrypt):
--     seller1   / 123456  -> ROLE_SELLER
--     seller2   / 123456  -> ROLE_SELLER
--     customer1 / 123456  -> ROLE_CUSTOMER
--     customer2 / 123456  -> ROLE_CUSTOMER
--     locked    / 123456  -> ROLE_CUSTOMER (bị khoá, enabled = 0)
--  "Khách" (GUEST) = người chưa đăng nhập, KHÔNG có dòng trong bảng users.
-- =====================================================================

DROP DATABASE IF EXISTS eshop;
CREATE DATABASE eshop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE eshop;

-- ---------------------------------------------------------------------
-- PHẦN A. DANH MỤC & SẢN PHẨM
-- ---------------------------------------------------------------------
CREATE TABLE category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uq_category_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE product (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)  NOT NULL,
    description VARCHAR(500),
    price       DECIMAL(15,2) NOT NULL,
    quantity    INT           NOT NULL DEFAULT 0,      -- số lượng tồn kho
    image_url   VARCHAR(500),
    category_id BIGINT        NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_quantity CHECK (quantity >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_product_name ON product (name);

INSERT INTO category (id, name, description) VALUES
 (1,  'Điện thoại',           'Điện thoại thông minh các hãng'),
 (2,  'Máy tính bảng',        'iPad, Galaxy Tab, Xiaomi Pad'),
 (3,  'Laptop',               'Máy tính xách tay học tập, văn phòng, đồ hoạ'),
 (4,  'Đồng hồ thông minh',   'Smartwatch, vòng đeo tay sức khoẻ'),
 (5,  'Tai nghe',             'Tai nghe có dây, không dây, chống ồn'),
 (6,  'Loa',                  'Loa Bluetooth, loa di động'),
 (7,  'Bàn phím',             'Bàn phím cơ, bàn phím không dây'),
 (8,  'Chuột',                'Chuột văn phòng, chuột gaming'),
 (9,  'Màn hình',             'Màn hình máy tính văn phòng, gaming, đồ hoạ'),
 (10, 'Máy ảnh',              'Máy ảnh mirrorless, máy ảnh chụp lấy liền'),
 (11, 'Phụ kiện sạc',         'Củ sạc, cáp sạc, pin dự phòng'),
 (12, 'Thiết bị mạng',        'Router WiFi, bộ mở rộng sóng, mesh'),
 (13, 'Thiết bị lưu trữ',     'Ổ cứng SSD, HDD, USB'),
 (14, 'Máy in',               'Máy in laser, máy in phun màu'),
 (15, 'Gia dụng thông minh',  'Robot hút bụi, nồi chiên, máy lọc không khí');

INSERT INTO product (name, description, price, quantity, image_url, category_id) VALUES
-- 1. Điện thoại
 ('iPhone 15 128GB',              'Chip A16 Bionic, màn hình 6.1 inch, Dynamic Island',      19990000, 25, 'https://placehold.co/400x300?text=iPhone+15',             1),
 ('Samsung Galaxy S24 256GB',     'Exynos 2400, màn hình 6.2 inch 120Hz, Galaxy AI',          22990000, 20, 'https://placehold.co/400x300?text=Galaxy+S24',            1),
 ('Xiaomi Redmi Note 13 8GB/128GB','Màn hình AMOLED 120Hz, camera 108MP, pin 5000mAh',          4890000, 60, 'https://placehold.co/400x300?text=Redmi+Note+13',         1),
 ('OPPO Reno11 F 5G',             'Màn hình 6.7 inch, sạc nhanh 67W, camera 64MP',             8990000, 35, 'https://placehold.co/400x300?text=OPPO+Reno11+F',         1),
-- 2. Máy tính bảng
 ('iPad Air M2 11 inch WiFi 128GB','Chip Apple M2, hỗ trợ Apple Pencil Pro',                   16990000, 15, 'https://placehold.co/400x300?text=iPad+Air+M2',          2),
 ('Samsung Galaxy Tab S9 FE',     'Màn hình 10.9 inch, kèm bút S Pen, kháng nước IP68',        9490000, 18, 'https://placehold.co/400x300?text=Galaxy+Tab+S9+FE',     2),
 ('Xiaomi Pad 6 8GB/256GB',       'Snapdragon 870, màn hình 11 inch 144Hz',                    7990000, 22, 'https://placehold.co/400x300?text=Xiaomi+Pad+6',          2),
-- 3. Laptop
 ('MacBook Air M3 13 inch 16GB/256GB','Chip Apple M3, pin đến 18 giờ, nặng 1.24kg',           27990000, 10, 'https://placehold.co/400x300?text=MacBook+Air+M3',       3),
 ('Dell Inspiron 15 3530',        'Core i5-1335U, RAM 16GB, SSD 512GB, 15.6 inch FHD',        15990000, 14, 'https://placehold.co/400x300?text=Dell+Inspiron+15',     3),
 ('ASUS Vivobook 15 X1504VA',     'Core i5-1335U, RAM 8GB, SSD 512GB',                        12490000, 20, 'https://placehold.co/400x300?text=ASUS+Vivobook+15',     3),
 ('Lenovo ThinkPad E14 Gen 5',    'Core i7-1355U, RAM 16GB, SSD 512GB, bàn phím chuẩn ThinkPad',18990000, 8, 'https://placehold.co/400x300?text=ThinkPad+E14',         3),
-- 4. Đồng hồ thông minh
 ('Apple Watch SE 2 GPS 40mm',    'Theo dõi nhịp tim, phát hiện té ngã, chống nước 50m',       6490000, 30, 'https://placehold.co/400x300?text=Apple+Watch+SE+2',     4),
 ('Samsung Galaxy Watch6 40mm',   'Đo thành phần cơ thể, theo dõi giấc ngủ',                   5990000, 25, 'https://placehold.co/400x300?text=Galaxy+Watch6',         4),
 ('Xiaomi Smart Band 8',          'Màn hình AMOLED 1.62 inch, pin 16 ngày',                     890000, 80, 'https://placehold.co/400x300?text=Smart+Band+8',          4),
-- 5. Tai nghe
 ('AirPods Pro 2 USB-C',          'Chống ồn chủ động, âm thanh không gian',                    5990000, 40, 'https://placehold.co/400x300?text=AirPods+Pro+2',         5),
 ('Sony WH-1000XM5',              'Tai nghe chụp tai chống ồn hàng đầu, pin 30 giờ',           7990000, 12, 'https://placehold.co/400x300?text=Sony+WH-1000XM5',      5),
 ('JBL Tune 520BT',               'Tai nghe Bluetooth chụp tai, pin 57 giờ',                   1190000, 50, 'https://placehold.co/400x300?text=JBL+Tune+520BT',        5),
 ('Soundcore Liberty 4 NC',       'Tai nghe True Wireless chống ồn, pin 50 giờ',               1790000, 45, 'https://placehold.co/400x300?text=Liberty+4+NC',          5),
-- 6. Loa
 ('JBL Flip 6',                   'Loa Bluetooth chống nước IP67, pin 12 giờ',                 2690000, 30, 'https://placehold.co/400x300?text=JBL+Flip+6',            6),
 ('Marshall Emberton II',         'Loa di động âm thanh 360 độ, pin hơn 30 giờ',               3990000, 15, 'https://placehold.co/400x300?text=Marshall+Emberton+II', 6),
 ('Sony SRS-XB100',               'Loa mini Extra Bass, có dây đeo',                           1290000, 40, 'https://placehold.co/400x300?text=Sony+SRS-XB100',        6),
-- 7. Bàn phím
 ('Akko 3087 Dragon Ball',        'Bàn phím cơ TKL, switch Akko CS Jelly Pink',                1290000, 25, 'https://placehold.co/400x300?text=Akko+3087',             7),
 ('Logitech K380',                'Bàn phím Bluetooth đa thiết bị, nhỏ gọn',                    790000, 60, 'https://placehold.co/400x300?text=Logitech+K380',         7),
 ('Keychron K2 V2',               'Bàn phím cơ không dây 75%, hỗ trợ Mac/Windows',             2190000, 20, 'https://placehold.co/400x300?text=Keychron+K2',           7),
-- 8. Chuột
 ('Logitech M331 Silent Plus',    'Chuột không dây, giảm 90% tiếng ồn',                         350000, 100,'https://placehold.co/400x300?text=Logitech+M331',         8),
 ('Logitech MX Master 3S',        'Chuột cao cấp 8000 DPI, cuộn MagSpeed',                     2490000, 20, 'https://placehold.co/400x300?text=MX+Master+3S',          8),
 ('Razer DeathAdder Essential',   'Chuột gaming 6400 DPI, thiết kế công thái học',              590000, 45, 'https://placehold.co/400x300?text=DeathAdder',            8),
-- 9. Màn hình
 ('LG 24MP60G 24 inch',           'Tấm nền IPS, 75Hz, AMD FreeSync',                           2890000, 30, 'https://placehold.co/400x300?text=LG+24MP60G',            9),
 ('Dell UltraSharp U2424H',       '24 inch IPS 120Hz, chuẩn màu 100% sRGB',                    5990000, 12, 'https://placehold.co/400x300?text=Dell+U2424H',           9),
 ('Samsung Odyssey G5 27 inch',   'Màn hình cong 2K 165Hz cho game thủ',                       6490000, 10, 'https://placehold.co/400x300?text=Odyssey+G5',            9),
 ('ASUS ProArt PA248QV',          '24.1 inch, 100% sRGB, Calman Verified cho đồ hoạ',          5290000, 8,  'https://placehold.co/400x300?text=ProArt+PA248QV',        9),
-- 10. Máy ảnh
 ('Canon EOS R50 Kit 18-45mm',    'Mirrorless 24.2MP, quay 4K, nhỏ gọn',                      18990000, 6,  'https://placehold.co/400x300?text=Canon+EOS+R50',        10),
 ('Sony ZV-E10 Kit 16-50mm',      'Máy ảnh vlog ống kính rời, quay 4K',                       16490000, 7,  'https://placehold.co/400x300?text=Sony+ZV-E10',          10),
 ('Fujifilm Instax Mini 12',      'Máy ảnh chụp lấy liền, tự động chỉnh sáng',                 1890000, 35, 'https://placehold.co/400x300?text=Instax+Mini+12',       10),
-- 11. Phụ kiện sạc
 ('Củ sạc Anker 20W USB-C',       'Sạc nhanh PD 20W cho iPhone',                                290000, 120,'https://placehold.co/400x300?text=Anker+20W',             11),
 ('Pin dự phòng Xiaomi 20000mAh', 'Sạc nhanh 22.5W, 2 cổng USB-A + 1 USB-C',                    590000, 70, 'https://placehold.co/400x300?text=Xiaomi+20000mAh',       11),
 ('Cáp Ugreen USB-C to USB-C 100W','Dài 1.5m, bọc dù, hỗ trợ sạc laptop',                       250000, 150,'https://placehold.co/400x300?text=Ugreen+100W',           11),
 ('Củ sạc Baseus GaN 65W',        'Công nghệ GaN, 2 USB-C + 1 USB-A, sạc được laptop',          790000, 55, 'https://placehold.co/400x300?text=Baseus+65W',            11),
-- 12. Thiết bị mạng
 ('Router TP-Link Archer AX23',   'WiFi 6 chuẩn AX1800, 4 ăng-ten',                            1290000, 30, 'https://placehold.co/400x300?text=Archer+AX23',           12),
 ('TP-Link Deco X20 (2-pack)',    'Hệ thống WiFi Mesh WiFi 6, phủ sóng 370m2',                 3490000, 15, 'https://placehold.co/400x300?text=Deco+X20',              12),
-- 13. Thiết bị lưu trữ
 ('SSD di động Samsung T7 1TB',   'USB 3.2 Gen 2, tốc độ đọc 1050MB/s',                        2690000, 25, 'https://placehold.co/400x300?text=Samsung+T7',            13),
 ('USB SanDisk Ultra 64GB',       'USB 3.0, tốc độ đọc 130MB/s',                                190000, 200,'https://placehold.co/400x300?text=SanDisk+64GB',          13),
 ('SSD WD Blue SN580 1TB NVMe',   'PCIe Gen4, tốc độ đọc 4150MB/s',                            1890000, 30, 'https://placehold.co/400x300?text=WD+SN580',              13),
 ('HDD Seagate One Touch 2TB',    'Ổ cứng di động USB 3.0, vỏ vải sang trọng',                 2190000, 20, 'https://placehold.co/400x300?text=Seagate+2TB',           13),
-- 14. Máy in
 ('Canon LBP2900',                'Máy in laser trắng đen A4, 12 trang/phút',                  3490000, 10, 'https://placehold.co/400x300?text=Canon+LBP2900',        14),
 ('HP LaserJet M211dw',           'In 2 mặt tự động, kết nối WiFi',                            4290000, 8,  'https://placehold.co/400x300?text=HP+M211dw',             14),
 ('Epson EcoTank L3250',          'In phun màu đa năng in-scan-copy, WiFi',                    4590000, 9,  'https://placehold.co/400x300?text=Epson+L3250',           14),
-- 15. Gia dụng thông minh
 ('Robot hút bụi Xiaomi E10',     'Hút và lau 2 trong 1, lực hút 4000Pa',                      3990000, 12, 'https://placehold.co/400x300?text=Xiaomi+E10',            15),
 ('Nồi chiên không dầu Philips HD9200','Dung tích 4.1L, công nghệ Rapid Air',                    1990000, 25, 'https://placehold.co/400x300?text=Philips+HD9200',       15),
 ('Máy lọc không khí Xiaomi 4 Lite','Màng lọc HEPA, điều khiển qua app Mi Home',               3290000, 18, 'https://placehold.co/400x300?text=Air+Purifier+4+Lite',  15);

-- ---------------------------------------------------------------------
-- PHẦN B. TÀI KHOẢN (Spring Security)
-- role: ROLE_SELLER | ROLE_CUSTOMER   (ROLE_GUEST gán cho người chưa đăng nhập)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(100) NOT NULL,                 -- BCrypt 60 ký tự
    full_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(100) NOT NULL,
    phone      VARCHAR(20),
    address    VARCHAR(255),
    role       VARCHAR(30)  NOT NULL DEFAULT 'ROLE_CUSTOMER',
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ROLE_SELLER', 'ROLE_CUSTOMER'))
) ENGINE = InnoDB;

-- Mật khẩu: 123456
INSERT INTO users (username, password, full_name, email, phone, address, role, enabled) VALUES
 ('seller1',   '$2a$10$uPUNNrC1JUnzBYDZD6AQBe.swyBhdnARmWl.Ctpo737mZZF4hejyS', 'Trần Văn Bán',  'seller1@eshop.vn',   '0901000001', '12 Nguyễn Văn Bảo, Gò Vấp, TP.HCM', 'ROLE_SELLER',   TRUE),
 ('seller2',   '$2a$10$uPUNNrC1JUnzBYDZD6AQBe.swyBhdnARmWl.Ctpo737mZZF4hejyS', 'Lê Thị Hàng',   'seller2@eshop.vn',   '0901000002', '45 Lê Lợi, Quận 1, TP.HCM',         'ROLE_SELLER',   TRUE),
 ('customer1', '$2a$10$uPUNNrC1JUnzBYDZD6AQBe.swyBhdnARmWl.Ctpo737mZZF4hejyS', 'Nguyễn Văn Mua','customer1@gmail.com','0912000001', '78 Phan Văn Trị, Bình Thạnh, TP.HCM','ROLE_CUSTOMER', TRUE),
 ('customer2', '$2a$10$uPUNNrC1JUnzBYDZD6AQBe.swyBhdnARmWl.Ctpo737mZZF4hejyS', 'Phạm Thị Sắm',  'customer2@gmail.com','0912000002', '9 Võ Văn Ngân, Thủ Đức, TP.HCM',    'ROLE_CUSTOMER', TRUE),
 ('locked',    '$2a$10$uPUNNrC1JUnzBYDZD6AQBe.swyBhdnARmWl.Ctpo737mZZF4hejyS', 'Tài Khoản Khoá','locked@gmail.com',   '0912000009', 'TP.HCM',                            'ROLE_CUSTOMER', FALSE);

-- ---------------------------------------------------------------------
-- PHẦN C. ĐƠN HÀNG (Giỏ hàng + Thanh toán)
-- user_id cho phép NULL để Phần 2 (chưa có Security) vẫn lưu được đơn.
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_date     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    customer_name  VARCHAR(100)  NOT NULL,
    phone          VARCHAR(20)   NOT NULL,
    address        VARCHAR(255)  NOT NULL,
    note           VARCHAR(500),
    payment_method VARCHAR(20)   NOT NULL,            -- COD | BANKING
    status         VARCHAR(20)   NOT NULL DEFAULT 'NEW', -- NEW | PAID | SHIPPING | DONE | CANCELLED
    total_amount   DECIMAL(15,2) NOT NULL,
    user_id        BIGINT        NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE order_detail (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,                -- giá tại thời điểm mua
    CONSTRAINT pk_order_detail PRIMARY KEY (id),
    CONSTRAINT fk_od_order   FOREIGN KEY (order_id)   REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_od_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_od_quantity CHECK (quantity > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- KIỂM TRA
-- ---------------------------------------------------------------------
SELECT 'category' AS tbl, COUNT(*) AS total FROM category
UNION ALL SELECT 'product', COUNT(*) FROM product
UNION ALL SELECT 'users',   COUNT(*) FROM users;
-- Kết quả mong đợi: category 15, product 50, users 5
