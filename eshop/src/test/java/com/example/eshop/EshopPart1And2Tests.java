package com.example.eshop;

import com.example.eshop.entity.Category;
import com.example.eshop.entity.Order;
import com.example.eshop.entity.OrderStatus;
import com.example.eshop.entity.Product;
import com.example.eshop.repository.CategoryRepository;
import com.example.eshop.repository.OrderRepository;
import com.example.eshop.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class EshopPart1And2Tests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Phần 1: Xem trang chủ - 12 sản phẩm/trang, 15 danh mục")
    void testShopIndex() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/index"))
                .andExpect(model().attributeExists("products", "categories"))
                .andExpect(model().attribute("categories", hasSize(15)))
                .andExpect(model().attribute("products", hasProperty("totalElements", is(50L))));
    }

    @Test
    @DisplayName("Phần 1: Lọc theo danh mục Laptop (id = 3)")
    void testFilterByCategoryLaptop() throws Exception {
        mockMvc.perform(get("/products").param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("products", hasProperty("totalElements", is(4L))));
    }

    @Test
    @DisplayName("Phần 1: Tìm kiếm theo từ khoá 'samsung'")
    void testSearchByKeyword() throws Exception {
        mockMvc.perform(get("/products").param("keyword", "samsung"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("products", hasProperty("totalElements", is(5L))));
    }

    @Test
    @DisplayName("Phần 1: Thêm danh mục trùng tên báo lỗi duplicate")
    void testDuplicateCategory() throws Exception {
        mockMvc.perform(post("/admin/categories/save")
                        .param("name", "Điện thoại")
                        .param("description", "Trùng lặp"))
                .andExpect(view().name("admin/category/form"))
                .andExpect(model().hasErrors());
    }

    @Test
    @DisplayName("Phần 1: Xoá danh mục đang có sản phẩm báo lỗi")
    void testDeleteCategoryWithProducts() throws Exception {
        mockMvc.perform(post("/admin/categories/3/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("Phần 1: Thêm danh mục mới rồi xoá thành công")
    void testAddAndDeleteCategory() throws Exception {
        Category cat = new Category();
        cat.setName("Danh mục test tạm");
        cat.setDescription("Tạm thời");
        cat = categoryRepository.save(cat);

        mockMvc.perform(post("/admin/categories/" + cat.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("message", "Đã xoá danh mục"));

        assertThat(categoryRepository.findById(cat.getId())).isEmpty();
    }

    @Test
    @DisplayName("Phần 1: Thêm sản phẩm để trống tên và giá âm báo lỗi validation")
    void testAddProductValidationErrors() throws Exception {
        mockMvc.perform(post("/admin/products/save")
                        .param("name", "")
                        .param("price", "-1000")
                        .param("quantity", "10")
                        .param("categoryId", "1"))
                .andExpect(view().name("admin/product/form"))
                .andExpect(model().hasErrors());
    }

    @Test
    @DisplayName("Phần 1: Sửa giá sản phẩm cập nhật giá nhưng createdAt giữ nguyên")
    void testUpdateProductPriceKeepsCreatedAt() throws Exception {
        Product p = productRepository.findById(1L).orElseThrow();
        LocalDateTime originalCreatedAt = p.getCreatedAt();
        BigDecimal originalPrice = p.getPrice();

        mockMvc.perform(post("/admin/products/save")
                        .param("id", "1")
                        .param("name", p.getName())
                        .param("description", p.getDescription())
                        .param("price", "20500000")
                        .param("quantity", String.valueOf(p.getQuantity()))
                        .param("imageUrl", p.getImageUrl())
                        .param("categoryId", String.valueOf(p.getCategory().getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        Product updated = productRepository.findById(1L).orElseThrow();
        assertThat(updated.getPrice()).isEqualByComparingTo("20500000");
        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);

        // restore original price
        updated.setPrice(originalPrice);
        productRepository.save(updated);
    }

    @Test
    @Transactional
    @DisplayName("Phần 2: Giỏ hàng thêm, cập nhật, đặt hàng COD và trừ tồn kho")
    void testCartAndCheckoutFlow() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Thêm sản phẩm 1 (iPhone 15) vào giỏ với số lượng 2
        mockMvc.perform(post("/cart/add")
                        .session(session)
                        .param("productId", "1")
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        // Kiểm tra giỏ hàng trong session
        mockMvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("cart/view"))
                .andExpect(content().string(containsString("39.980.000")));

        // 2. Thêm số lượng vượt quá tồn kho -> báo lỗi
        Product p1 = productRepository.findById(1L).orElseThrow();
        mockMvc.perform(post("/cart/add")
                        .session(session)
                        .param("productId", "1")
                        .param("quantity", String.valueOf(p1.getQuantity() + 10)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/1"))
                .andExpect(flash().attributeExists("error"));

        // 3. Đặt hàng COD
        int stockBefore = p1.getQuantity();
        mockMvc.perform(post("/checkout")
                        .session(session)
                        .param("customerName", "Nguyễn Văn Mua")
                        .param("phone", "0912000001")
                        .param("address", "78 Phan Văn Trị, Bình Thạnh, TP.HCM")
                        .param("paymentMethod", "COD"))
                .andExpect(status().is3xxRedirection());

        // Kiểm tra tồn kho đã giảm đi 2
        Product p1After = productRepository.findById(1L).orElseThrow();
        assertThat(p1After.getQuantity()).isEqualTo(stockBefore - 2);

        // 4. Hủy đơn và hoàn tồn kho
        Order order = orderRepository.findAllByOrderByOrderDateDesc().get(0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);

        mockMvc.perform(post("/admin/orders/" + order.getId() + "/status")
                        .param("status", "CANCELLED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + order.getId()));

        Product p1Restored = productRepository.findById(1L).orElseThrow();
        assertThat(p1Restored.getQuantity()).isEqualTo(stockBefore);
    }
}
