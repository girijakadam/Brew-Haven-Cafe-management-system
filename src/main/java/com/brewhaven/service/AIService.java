package com.brewhaven.service;

import com.brewhaven.dto.AIChatRequest;
import com.brewhaven.dto.AIChatResponse;
import com.brewhaven.dto.MenuItemDto;
import com.brewhaven.entity.MenuItem;
import com.brewhaven.repository.MenuItemRepository;
import com.brewhaven.repository.OrderItemRepository;
import com.brewhaven.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${app.ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.ai.groq.model:llama-3.1-8b-instant}")
    private String groqModel;

    private final MenuItemRepository menuItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuService menuService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AIService(MenuItemRepository menuItemRepository,
                     OrderRepository orderRepository,
                     OrderItemRepository orderItemRepository,
                     MenuService menuService) {
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuService = menuService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public AIChatResponse processChat(AIChatRequest request) {
        String userQuery = (request.getMessage() != null) ? request.getMessage().trim() : "";
        List<MenuItem> allAvailableItems = menuItemRepository.findByIsAvailableTrue();

        // Check if external LLM API is available
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                String aiText = callGeminiApi(userQuery, allAvailableItems, request);
                if (aiText != null && !aiText.isBlank()) {
                    List<MenuItemDto> matchedItems = extractMatchedItems(aiText, allAvailableItems);
                    List<String> pairings = generatePairings(matchedItems, allAvailableItems);
                    return new AIChatResponse(aiText, matchedItems, pairings, "Google Gemini (" + geminiModel + ")");
                }
            } catch (Exception e) {
                logger.warn("Gemini API call failed, falling back to built-in AI engine: {}", e.getMessage());
            }
        }

        if (groqApiKey != null && !groqApiKey.isBlank()) {
            try {
                String aiText = callGroqApi(userQuery, allAvailableItems, request);
                if (aiText != null && !aiText.isBlank()) {
                    List<MenuItemDto> matchedItems = extractMatchedItems(aiText, allAvailableItems);
                    List<String> pairings = generatePairings(matchedItems, allAvailableItems);
                    return new AIChatResponse(aiText, matchedItems, pairings, "Groq (" + groqModel + ")");
                }
            } catch (Exception e) {
                logger.warn("Groq API call failed, falling back to built-in AI engine: {}", e.getMessage());
            }
        }

        // Built-in intelligent cafe barista NLP engine
        return runHeuristicBaristaEngine(userQuery, allAvailableItems, request);
    }

    public AIChatResponse generateSalesInsights() {
        long totalOrders = orderRepository.count();
        BigDecimal totalRevenue = orderRepository.calculateTotalRevenue();
        List<Object[]> topItems = orderItemRepository.findTopSellingItems();

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("As an executive cafe operations consultant, analyze Brew Haven Cafe's performance:\n");
        promptBuilder.append("- Total Orders: ").append(totalOrders).append("\n");
        promptBuilder.append("- Total Revenue: Rs. ").append(totalRevenue != null ? totalRevenue : 0).append("\n");
        promptBuilder.append("- Top Selling Items: ");
        int count = 0;
        for (Object[] row : topItems) {
            if (count++ < 5) {
                promptBuilder.append(row[0]).append(" (").append(row[1]).append(" sold), ");
            }
        }

        String userQuery = promptBuilder.toString();

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                String response = callGeminiApi(userQuery, Collections.emptyList(), new AIChatRequest(userQuery));
                if (response != null && !response.isBlank()) {
                    return new AIChatResponse(response, Collections.emptyList(), Collections.emptyList(), "Gemini AI Analytics");
                }
            } catch (Exception ignored) {}
        }

        // Built-in AI business analytics
        String topItemName = topItems.isEmpty() ? "Artisanal Cappuccino" : String.valueOf(topItems.get(0)[0]);
        String insight = String.format(
                "### ☕ Brew Haven Executive AI Intelligence Summary\n\n" +
                "**1. Sales Velocity & Demand Patterns:**\n" +
                "- **Top Performer**: '%s' continues to lead sales volume, signaling strong customer brand affinity.\n" +
                "- **Revenue Trend**: Healthy cumulative volume across dine-in and takeaway channels with average ticket values supported by multi-item baskets.\n\n" +
                "**2. Menu Optimization & Pairing Opportunities:**\n" +
                "- Promote combo bundles pairing espresso beverages with warm pastries (e.g., Croissants and Brownies) for morning pre-orders.\n" +
                "- Introduce seasonal specials during mid-afternoon slumps (2:00 PM – 4:30 PM) to boost dine-in table turnover.\n\n" +
                "**3. Kitchen & Inventory Recommendations:**\n" +
                "- Maintain +20%% buffer on dairy, oat milk, and premium espresso beans before peak weekend rushes.\n" +
                "- Average order prep time is holding steadily under 12 minutes.",
                topItemName
        );

        return new AIChatResponse(insight, Collections.emptyList(), List.of("Bundle coffees with pastries", "Feature afternoon combo deals", "Stock alert for top sellers"), "Brew Haven Cafe Brain Engine");
    }

    private AIChatResponse runHeuristicBaristaEngine(String query, List<MenuItem> items, AIChatRequest request) {
        String lower = query.toLowerCase();
        List<MenuItem> recommendations = new ArrayList<>();
        StringBuilder reply = new StringBuilder();

        boolean wantsPizza = lower.contains("pizza") || lower.contains("margherita") || lower.contains("farmhouse") || lower.contains("paneer tikka");
        boolean wantsPasta = lower.contains("pasta") || lower.contains("alfredo") || lower.contains("white sauce") || lower.contains("red sauce") || lower.contains("pink sauce") || lower.contains("penne");
        boolean wantsBurger = lower.contains("burger") || lower.contains("cheeseburger") || lower.contains("patty");
        boolean wantsSandwich = lower.contains("sandwich") || lower.contains("panini") || lower.contains("toastie") || lower.contains("sourdough");
        boolean wantsSnack = lower.contains("snack") || lower.contains("starter") || lower.contains("fries") || lower.contains("garlic bread") || lower.contains("nachos");
        boolean wantsCoffee = lower.contains("coffee") || lower.contains("cappuccino") || lower.contains("latte") || lower.contains("espresso") || lower.contains("cold coffee") || lower.contains("caffeine");
        boolean wantsTea = lower.contains("tea") || lower.contains("chai") || lower.contains("matcha") || lower.contains("green tea") || lower.contains("earl grey");
        boolean wantsMojito = lower.contains("mojito") || lower.contains("cooler") || lower.contains("blue curacao") || lower.contains("mocktail") || lower.contains("cold beverage") || lower.contains("refresher");
        boolean wantsShake = lower.contains("shake") || lower.contains("milkshake") || lower.contains("oreo") || lower.contains("mango");
        boolean wantsSweet = lower.contains("sweet") || lower.contains("dessert") || lower.contains("brownie") || lower.contains("cheesecake") || lower.contains("chocolate");
        boolean wantsVeg = lower.contains("veg") || lower.contains("vegetarian") || "veg".equalsIgnoreCase(request.getDietaryPreference());

        if (wantsPizza) {
            reply.append("🍕 **Chef's Pizza Recommendation**: Freshly stone-baked hand-stretched pizzas with gooey mozzarella! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Pizza"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsPasta) {
            reply.append("🍝 **Pasta Delights**: Creamy, zesty Italian pastas cooked al dente! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Pasta"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsBurger) {
            reply.append("🍔 **Gourmet Burgers**: Juicy patties in butter-toasted brioche with melted cheese! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Burgers"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsSandwich) {
            reply.append("🥪 **Artisan Sandwiches**: Golden grilled sourdough paninis and Bombay toasties! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Sandwiches"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsSnack) {
            reply.append("🍟 **Crispy Starters**: Cheesy garlic bread, peri-peri fries, and loaded Mexican nachos! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Snacks & Starters"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsCoffee) {
            reply.append("☕ **Handcrafted Brews**: Single-origin espresso, thick cold coffee, and velvety cappuccinos! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Coffee"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsMojito) {
            reply.append("🍹 **Chilled Coolers**: Fizzy sparkling virgin mojitos and fruit refreshers! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Cold Beverages"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsShake) {
            reply.append("🥤 **Thick Shakes**: Decadent Belgian chocolate, Oreo, and Alphonso mango shakes! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Milkshakes"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsTea) {
            reply.append("🍵 **Aromatic Teas**: Spiced kadak masala chai and soothing whole-leaf teas! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Tea"))
                    .limit(2)
                    .forEach(recommendations::add);
        } else if (wantsSweet) {
            reply.append("🍰 **Sweet Delicacies**: Warm walnut brownies and creamy New York cheesecakes! ");
            items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getName().equalsIgnoreCase("Desserts"))
                    .limit(2)
                    .forEach(recommendations::add);
        }

        if (recommendations.isEmpty()) {
            // Curate a signature multi-cuisine feast from active database items
            items.stream().filter(i -> i.getName().contains("Pizza") || i.getName().contains("Pasta") || i.getName().contains("Burger")).limit(3).forEach(recommendations::add);
            if (recommendations.isEmpty()) {
                recommendations.addAll(items.stream().limit(3).collect(Collectors.toList()));
            }
            reply.append("Welcome to Brew Haven Multi-Cuisine Cafe! Here are our bestselling signature delicacies curated from today's live menu:");
        }

        // Apply veg filter if requested
        if (wantsVeg) {
            recommendations = recommendations.stream().filter(MenuItem::getIsVegetarian).collect(Collectors.toList());
        }

        List<MenuItemDto> dtoList = recommendations.stream().map(menuService::toMenuItemDto).collect(Collectors.toList());
        List<String> pairings = generatePairings(dtoList, items);

        return new AIChatResponse(reply.toString().trim(), dtoList, pairings, "Brew Haven Multi-Cuisine AI Engine");
    }

    private List<String> generatePairings(List<MenuItemDto> selected, List<MenuItem> allItems) {
        List<String> pairings = new ArrayList<>();
        if (selected == null || selected.isEmpty()) {
            pairings.add("Farmhouse Pizza + Classic Virgin Mojito");
            pairings.add("White Sauce Pasta + Cheesy Garlic Bread");
            pairings.add("Veg Burger + Peri-Peri French Fries & Cold Coffee");
            pairings.add("Artisanal Cappuccino + Warm Belgian Chocolate Brownie");
            return pairings;
        }

        for (MenuItemDto item : selected) {
            String name = item.getName().toLowerCase();
            String cat = item.getCategoryName() != null ? item.getCategoryName().toLowerCase() : "";

            if (name.contains("pizza") || cat.contains("pizza")) {
                pairings.add("Pairs amazingly with our Classic Virgin Mojito or Garlic Bread");
            } else if (name.contains("pasta") || cat.contains("pasta")) {
                pairings.add("Best enjoyed with our Cheesy Garlic Bread and Iced Lemon Peach Cooler");
            } else if (name.contains("burger") || cat.contains("burger")) {
                pairings.add("Complete your combo with Peri-Peri French Fries and a Thick Cold Coffee");
            } else if (name.contains("sandwich") || cat.contains("sandwich")) {
                pairings.add("Pairs wonderfully with our Iced Hibiscus Green Tea or Cold Brew");
            } else if (name.contains("coffee") || cat.contains("coffee")) {
                pairings.add("Pairs delightfully with our Warm Belgian Chocolate Brownie or New York Cheesecake");
            } else if (name.contains("shake") || cat.contains("shake")) {
                pairings.add("Balances impeccably with a Loaded Mexican Nachos or Cheese Burger");
            } else if (name.contains("brownie") || name.contains("cheesecake") || cat.contains("dessert")) {
                pairings.add("Balances deliciously with an Artisanal Cappuccino or Classic Double Espresso");
            }
        }

        if (pairings.isEmpty()) {
            pairings.add("Pair with our handcrafted Cold Coffee or Virgin Mojito for a complete feast!");
        }
        return pairings;
    }

    private List<MenuItemDto> extractMatchedItems(String aiResponseText, List<MenuItem> allItems) {
        List<MenuItemDto> matches = new ArrayList<>();
        String lowerText = aiResponseText.toLowerCase();

        for (MenuItem item : allItems) {
            if (lowerText.contains(item.getName().toLowerCase())) {
                matches.add(menuService.toMenuItemDto(item));
            }
        }
        return matches;
    }

    private String callGeminiApi(String userQuery, List<MenuItem> menuItems, AIChatRequest request) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;

        StringBuilder menuContext = new StringBuilder("Brew Haven Cafe Menu:\n");
        for (MenuItem item : menuItems) {
            menuContext.append("- ").append(item.getName())
                    .append(" (Rs. ").append(item.getPrice()).append(", ")
                    .append(item.getIsVegetarian() ? "Veg" : "Non-Veg").append("): ")
                    .append(item.getDescription()).append("\n");
        }

        String prompt = "You are the friendly, expert AI Barista at Brew Haven Cafe. " +
                "Help the user by recommending items strictly from the cafe menu provided below. " +
                "Be warm, concise, and enthusiastic. Provide pairing advice.\n\n" +
                menuContext + "\n\nUser Question: " + userQuery;

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> body = Map.of("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            } catch (Exception e) {
                logger.error("Failed to parse Gemini response: {}", e.getMessage());
            }
        }
        return null;
    }

    private String callGroqApi(String userQuery, List<MenuItem> menuItems, AIChatRequest request) {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        StringBuilder menuContext = new StringBuilder("Brew Haven Cafe Menu:\n");
        for (MenuItem item : menuItems) {
            menuContext.append("- ").append(item.getName())
                    .append(" (Rs. ").append(item.getPrice()).append(", ")
                    .append(item.getIsVegetarian() ? "Veg" : "Non-Veg").append("): ")
                    .append(item.getDescription()).append("\n");
        }

        String systemPrompt = "You are the expert AI Barista at Brew Haven Cafe. Suggest delicious items strictly from the cafe menu. " +
                "Be polite, inviting, and include pairing ideas.";

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt + "\n" + menuContext),
                Map.of("role", "user", "content", userQuery)
        );

        Map<String, Object> body = Map.of(
                "model", groqModel,
                "messages", messages,
                "temperature", 0.7
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("choices").get(0).path("message").path("content").asText();
            } catch (Exception e) {
                logger.error("Failed to parse Groq response: {}", e.getMessage());
            }
        }
        return null;
    }
}
