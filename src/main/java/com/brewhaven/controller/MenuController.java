package com.brewhaven.controller;

import com.brewhaven.dto.ApiResponse;
import com.brewhaven.dto.CategoryDto;
import com.brewhaven.dto.MenuItemDto;
import com.brewhaven.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories() {
        return ResponseEntity.ok(menuService.getAllCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryDto dto) {
        CategoryDto created = menuService.createCategory(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDto> updateCategory(@PathVariable Long id, @RequestBody CategoryDto dto) {
        CategoryDto updated = menuService.updateCategory(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok("Category deleted successfully"));
    }

    @GetMapping("/all-items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MenuItemDto>> getAllItemsForAdmin() {
        return ResponseEntity.ok(menuService.getAllMenuItems());
    }

    @GetMapping("/items")
    public ResponseEntity<List<MenuItemDto>> getMenuItems(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean isVegetarian,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String query) {

        if (categoryId != null || isVegetarian != null || maxPrice != null || (query != null && !query.trim().isEmpty())) {
            return ResponseEntity.ok(menuService.filterMenuItems(categoryId, isVegetarian, maxPrice, query));
        }
        return ResponseEntity.ok(menuService.getAvailableMenuItems());
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<MenuItemDto> getMenuItemById(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getMenuItemById(id));
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MenuItemDto> createMenuItem(@Valid @RequestBody MenuItemDto dto) {
        MenuItemDto created = menuService.createMenuItem(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MenuItemDto> updateMenuItem(@PathVariable Long id, @RequestBody MenuItemDto dto) {
        MenuItemDto updated = menuService.updateMenuItem(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> deleteMenuItem(@PathVariable Long id) {
        menuService.deleteMenuItem(id);
        return ResponseEntity.ok(ApiResponse.ok("Menu item deleted successfully"));
    }

    @PatchMapping("/items/{id}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MenuItemDto> toggleAvailability(@PathVariable Long id, @RequestBody java.util.Map<String, Boolean> body) {
        Boolean available = body.getOrDefault("available", true);
        MenuItemDto updated = menuService.toggleItemAvailability(id, available);
        return ResponseEntity.ok(updated);
    }
}
