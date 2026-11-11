package com.brewhaven.dto;

import java.util.List;

public class AIChatResponse {

    private String reply;
    private List<MenuItemDto> suggestedItems;
    private List<String> pairingSuggestions;
    private String modelUsed;

    public AIChatResponse() {
    }

    public AIChatResponse(String reply, List<MenuItemDto> suggestedItems, List<String> pairingSuggestions, String modelUsed) {
        this.reply = reply;
        this.suggestedItems = suggestedItems;
        this.pairingSuggestions = pairingSuggestions;
        this.modelUsed = modelUsed;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<MenuItemDto> getSuggestedItems() {
        return suggestedItems;
    }

    public void setSuggestedItems(List<MenuItemDto> suggestedItems) {
        this.suggestedItems = suggestedItems;
    }

    public List<String> getPairingSuggestions() {
        return pairingSuggestions;
    }

    public void setPairingSuggestions(List<String> pairingSuggestions) {
        this.pairingSuggestions = pairingSuggestions;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }
}
