package com.brewhaven.dto;

import java.util.List;

public class AIChatRequest {

    private String message;
    private String conversationContext; // e.g. "customer_ordering", "staff_assist", "admin_insights"
    private List<String> currentCartItems;
    private String dietaryPreference; // "veg", "non-veg", "all"
    private String moodOrOccasion; // "quick bite", "relaxing coffee", "sweet treat"

    public AIChatRequest() {
    }

    public AIChatRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationContext() {
        return conversationContext;
    }

    public void setConversationContext(String conversationContext) {
        this.conversationContext = conversationContext;
    }

    public List<String> getCurrentCartItems() {
        return currentCartItems;
    }

    public void setCurrentCartItems(List<String> currentCartItems) {
        this.currentCartItems = currentCartItems;
    }

    public String getDietaryPreference() {
        return dietaryPreference;
    }

    public void setDietaryPreference(String dietaryPreference) {
        this.dietaryPreference = dietaryPreference;
    }

    public String getMoodOrOccasion() {
        return moodOrOccasion;
    }

    public void setMoodOrOccasion(String moodOrOccasion) {
        this.moodOrOccasion = moodOrOccasion;
    }
}
