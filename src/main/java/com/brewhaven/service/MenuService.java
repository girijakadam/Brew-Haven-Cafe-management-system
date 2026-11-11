package com.brewhaven.service;

import com.brewhaven.dto.CategoryDto;
import com.brewhaven.dto.MenuItemDto;
import com.brewhaven.entity.Category;
import com.brewhaven.entity.MenuItem;
import com.brewhaven.entity.OrderItem;
import com.brewhaven.exception.ResourceNotFoundException;
import com.brewhaven.repository.CategoryRepository;
import com.brewhaven.repository.MenuItemRepository;
import com.brewhaven.repository.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;

    public MenuService(MenuItemRepository menuItemRepository,
                       CategoryRepository categoryRepository,
                       OrderItemRepository orderItemRepository) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Category getCategoryEntity(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    @Transactional
    public CategoryDto createCategory(CategoryDto dto) {
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setImageUrl(dto.getImageUrl());
        category.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0);

        Category saved = categoryRepository.save(category);
        return toCategoryDto(saved);
    }

    @Transactional(readOnly = true)
    public List<MenuItemDto> getAllMenuItems() {
        return menuItemRepository.findAll()
                .stream()
                .map(this::toMenuItemDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MenuItemDto> getAvailableMenuItems() {
        return menuItemRepository.findByIsAvailableTrue()
                .stream()
                .map(this::toMenuItemDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MenuItemDto getMenuItemById(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        return toMenuItemDto(item);
    }

    @Transactional(readOnly = true)
    public MenuItem getMenuItemEntity(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
    }

    @Transactional(readOnly = true)
    public List<MenuItemDto> filterMenuItems(Long categoryId, Boolean isVegetarian, BigDecimal maxPrice, String query) {
        String cleanQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        return menuItemRepository.filterMenu(categoryId, isVegetarian, maxPrice, cleanQuery)
                .stream()
                .map(this::toMenuItemDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MenuItemDto createMenuItem(MenuItemDto dto) {
        Category category = getCategoryEntity(dto.getCategoryId());

        MenuItem item = new MenuItem();
        item.setName(dto.getName());
        item.setDescription(dto.getDescription());
        item.setPrice(dto.getPrice());
        item.setCategory(category);
        item.setIsVegetarian(dto.getIsVegetarian() != null ? dto.getIsVegetarian() : true);
        item.setIsAvailable(dto.getIsAvailable() != null ? dto.getIsAvailable() : true);
        item.setImageUrl(dto.getImageUrl());
        item.setPreparationTimeMinutes(dto.getPreparationTimeMinutes() != null ? dto.getPreparationTimeMinutes() : 10);
        item.setSpicyLevel(dto.getSpicyLevel() != null ? dto.getSpicyLevel() : "Mild");

        MenuItem saved = menuItemRepository.save(item);
        return toMenuItemDto(saved);
    }

    @Transactional
    public MenuItemDto updateMenuItem(Long id, MenuItemDto dto) {
        MenuItem item = getMenuItemEntity(id);

        if (dto.getName() != null) item.setName(dto.getName());
        if (dto.getDescription() != null) item.setDescription(dto.getDescription());
        if (dto.getPrice() != null) item.setPrice(dto.getPrice());
        if (dto.getCategoryId() != null) {
            Category category = getCategoryEntity(dto.getCategoryId());
            item.setCategory(category);
        }
        if (dto.getIsVegetarian() != null) item.setIsVegetarian(dto.getIsVegetarian());
        if (dto.getIsAvailable() != null) item.setIsAvailable(dto.getIsAvailable());
        if (dto.getImageUrl() != null) item.setImageUrl(dto.getImageUrl());
        if (dto.getPreparationTimeMinutes() != null) item.setPreparationTimeMinutes(dto.getPreparationTimeMinutes());
        if (dto.getSpicyLevel() != null) item.setSpicyLevel(dto.getSpicyLevel());

        MenuItem updated = menuItemRepository.save(item);
        return toMenuItemDto(updated);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryDto dto) {
        Category category = getCategoryEntity(id);
        if (dto.getName() != null) category.setName(dto.getName());
        if (dto.getDescription() != null) category.setDescription(dto.getDescription());
        if (dto.getImageUrl() != null) category.setImageUrl(dto.getImageUrl());
        if (dto.getDisplayOrder() != null) category.setDisplayOrder(dto.getDisplayOrder());
        Category saved = categoryRepository.save(category);
        return toCategoryDto(saved);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = getCategoryEntity(id);
        categoryRepository.delete(category);
    }

    @Transactional
    public MenuItemDto toggleItemAvailability(Long id, Boolean available) {
        MenuItem item = getMenuItemEntity(id);
        item.setIsAvailable(available);
        MenuItem updated = menuItemRepository.save(item);
        return toMenuItemDto(updated);
    }

    @Transactional
    public void deleteMenuItem(Long id) {
        MenuItem item = getMenuItemEntity(id);
        List<OrderItem> orderItems = orderItemRepository.findByMenuItemId(id);
        for (OrderItem oi : orderItems) {
            oi.setMenuItem(null);
        }
        orderItemRepository.saveAllAndFlush(orderItems);
        menuItemRepository.delete(item);
    }

    public MenuItemDto toMenuItemDto(MenuItem item) {
        MenuItemDto dto = new MenuItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setPrice(item.getPrice());
        if (item.getCategory() != null) {
            dto.setCategoryId(item.getCategory().getId());
            dto.setCategoryName(item.getCategory().getName());
        }
        dto.setIsVegetarian(item.getIsVegetarian());
        dto.setIsAvailable(item.getIsAvailable());
        dto.setImageUrl(item.getImageUrl());
        dto.setPreparationTimeMinutes(item.getPreparationTimeMinutes());
        dto.setSpicyLevel(item.getSpicyLevel());
        return dto;
    }

    public CategoryDto toCategoryDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setImageUrl(category.getImageUrl());
        dto.setDisplayOrder(category.getDisplayOrder());
        return dto;
    }
}
