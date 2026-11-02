package com.example.eshop.controller;

import com.example.eshop.ai.tracking.UserTrackingService;
import com.example.eshop.cart.Cart;
import com.example.eshop.dto.CheckoutForm;
import com.example.eshop.entity.Order;
import com.example.eshop.entity.PaymentMethod;
import com.example.eshop.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {
    private final Cart cart;
    private final OrderService orderService;
    private final UserTrackingService trackingService;

    @GetMapping
    public String form(Model model) {
        if (cart.isEmpty()) return "redirect:/cart";
        model.addAttribute("form", new CheckoutForm());
        model.addAttribute("cart", cart);
        return "checkout/form";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("form") CheckoutForm form, BindingResult result,
                         Model model, RedirectAttributes ra,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        if (result.hasErrors()) {
            model.addAttribute("cart", cart);
            return "checkout/form";
        }
        try {
            String sid = trackingService.resolveSessionId(request, response);
            Order order = orderService.placeOrder(form, cart);

            // Ghi nhận sự kiện Mua hàng (PURCHASE) vào User Tracking
            trackingService.trackPurchase(sid, order);

            cart.clear();
            if (order.getPaymentMethod() == PaymentMethod.BANKING) {
                return "redirect:/orders/" + order.getId() + "/payment";
            }
            ra.addFlashAttribute("message", "Đặt hàng thành công! Mã đơn #" + order.getId());
            return "redirect:/orders/" + order.getId();
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart";
        }
    }
}
