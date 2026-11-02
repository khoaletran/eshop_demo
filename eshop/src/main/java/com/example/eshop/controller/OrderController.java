package com.example.eshop.controller;

import com.example.eshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.findById(id));
        return "order/detail";
    }

    @GetMapping("/{id}/payment")
    public String payment(@PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.findById(id));
        return "order/payment";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, RedirectAttributes ra) {
        orderService.markPaid(id);
        ra.addFlashAttribute("message", "Thanh toán thành công đơn #" + id);
        return "redirect:/orders/" + id;
    }
}
