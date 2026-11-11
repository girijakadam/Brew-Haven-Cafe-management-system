package com.brewhaven.controller;

import com.brewhaven.dto.AIChatRequest;
import com.brewhaven.dto.AIChatResponse;
import com.brewhaven.service.AIService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AIChatResponse> chat(@RequestBody AIChatRequest request) {
        AIChatResponse response = aiService.processChat(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recommendations")
    public ResponseEntity<AIChatResponse> getRecommendations(
            @RequestParam(required = false) String context,
            @RequestParam(required = false) String dietaryPreference,
            @RequestParam(required = false) String mood) {
        AIChatRequest req = new AIChatRequest("Suggest signature cafe recommendations");
        req.setConversationContext(context);
        req.setDietaryPreference(dietaryPreference);
        req.setMoodOrOccasion(mood);

        AIChatResponse response = aiService.processChat(req);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/insights")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AIChatResponse> getSalesInsights() {
        AIChatResponse insights = aiService.generateSalesInsights();
        return ResponseEntity.ok(insights);
    }
}
