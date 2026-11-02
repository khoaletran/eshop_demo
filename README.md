# 🛒 eShop — Nền tảng Thương mại Điện tử tích hợp Trí tuệ Nhân tạo (AI & Vector Database)

Dự án đồ án môn học Công nghệ Web (WWW) — Xây dựng website mua sắm trực tuyến kết hợp hệ thống AI thông minh bao gồm **User Tracking**, **Vector Database** và **Chatbot RAG** dựa trên hệ sinh thái Spring Boot 3 & Google Gemini API.

---

## 🌟 Tính năng chính

### 1. Phân hệ E-Commerce (Spring Boot + MariaDB)
- **Danh mục & Sản phẩm:** Phân loại 15 danh mục, 50 sản phẩm công nghệ; phân trang 12 sản phẩm/trang; tìm kiếm từ khóa linh hoạt.
- **Giỏ hàng & Đơn hàng:** Lưu trữ giỏ hàng theo Session; quy trình đặt hàng COD trừ tồn kho tự động trong Transaction.
- **Quản trị (Admin):** Thêm, sửa, xóa sản phẩm và danh mục; bảo toàn ngày tạo `created_at` khi cập nhật giá.

### 2. Phân hệ Trí tuệ Nhân tạo (AI & Vector Database)
- **User Tracking:** Định danh khách vãng lai (Guest) qua Cookie 30 ngày; thu thập 4 hành vi (`VIEW`, `SEARCH`, `ADD_TO_CART`, `PURCHASE`) và đo thời gian dừng trang (Dwell Time).
- **Vector Database:** Chuyển đổi dữ liệu sản phẩm thành vector ngữ nghĩa 384 chiều bằng Google Gemini Embedding (`gemini-embedding-001`); lưu trữ bảng `product_vector` và nạp RAM cache để so khớp Cosine Similarity.
- **Recommendation Engine (Gợi ý cá nhân hóa):** Áp dụng trọng số Intent kết hợp hàm suy giảm phân rã thời gian lũy thừa (Recency Decay $0.5^{\Delta t / 72\text{h}}$) để tạo Vector sở thích người dùng và gợi ý Top sản phẩm phù hợp ở Trang chủ.
- **Chatbot RAG (Trợ lý ảo tư vấn bán hàng):** Mô hình Retrieval-Augmented Generation — truy xuất thông tin tồn kho thực tế rồi đưa vào Google Gemini LLM (`gemini-flash-lite-latest`) để tư vấn chính xác giá và đặc tính sản phẩm; hỗ trợ Widget chat nổi và trang `/chat`.

---

## 🛠️ Cài đặt & Khởi chạy

### 1. Yêu cầu môi trường
- Java 17+
- Maven 3.8+
- MariaDB 10.6+ / 11.x (Port mặc định: 3306)

### 2. Khởi tạo Cơ sở dữ liệu
Chạy lần lượt 2 file SQL trong thư mục `database/`:
1. `database/eShop.sql` — Tạo database `eshop`, bảng danh mục, sản phẩm và đơn hàng.
2. `database/ai_schema.sql` — Tạo bảng `user_event` và `product_vector`.

### 3. Cấu hình Biến môi trường
Sao chép file `.env.example` thành `.env` và điền cấu hình của bạn:
```properties
GEMINI_API_KEY=AIzaSyBxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
DB_HOST=localhost
DB_PORT=3306
DB_NAME=eshop
DB_USERNAME=root
DB_PASSWORD=123456
SERVER_PORT=8080
```

### 4. Chạy ứng dụng
```bash
cd eshop
mvn clean spring-boot:run
```
Truy cập:
- Trang mua sắm: [http://localhost:8080/](http://localhost:8080/)
- Trợ lý Chatbot AI: [http://localhost:8080/chat](http://localhost:8080/chat)
- Bảng điều khiển User Tracking: [http://localhost:8080/admin/tracking](http://localhost:8080/admin/tracking)

### 5. Chạy kiểm thử tự động (Unit & Integration Tests)
```bash
cd eshop
mvn test
```
*(Toàn bộ 19 test cases vượt qua 100%).*

---

## 📄 Tài liệu Báo cáo & Phân công công việc (4 Thành viên)
Chi tiết kịch bản thuyết trình, phân chia file code và bộ câu hỏi vấn đáp cho 4 người xem tại file Word:
👉 **`BaoCao_PhanChiaCongViec_AI_eShop.docx`**
