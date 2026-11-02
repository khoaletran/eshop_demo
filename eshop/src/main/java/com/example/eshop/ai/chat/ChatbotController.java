package com.example.eshop.ai.chat;

import com.example.eshop.ai.model.ChatDto;
import com.example.eshop.ai.tracking.UserTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final UserTrackingService trackingService;

    @PostMapping("/api/ai/chat")
    @ResponseBody
    public ResponseEntity<ChatDto.ChatResponse> apiChat(@RequestBody ChatDto.ChatRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        String sid = request.getSessionId();
        if (sid == null || sid.isBlank()) {
            sid = trackingService.resolveSessionId(httpRequest, httpResponse);
        }
        ChatDto.ChatResponse response = chatbotService.chat(request.getMessage(), sid);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat")
    public String chatPage(Model model, HttpServletRequest request, HttpServletResponse response) {
        String sid = trackingService.resolveSessionId(request, response);
        model.addAttribute("sessionId", sid);
        return "chat/index";
    }
}
