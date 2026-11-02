package com.example.eshop.ai.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class ChatDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ChatRequest {
        private String message;
        private String sessionId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ChatResponse {
        private String reply;
        private List<ProductSummary> suggestedProducts = new ArrayList<>();
        private List<String> quickFollowUps = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ProductSummary {
        private Long id;
        private String name;
        private String priceFormatted;
        private String imageUrl;
        private String category;
        private double score;
    }
}
