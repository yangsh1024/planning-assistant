package com.ysh.planning.plan.service;

import com.ysh.planning.common.security.UserContext;
import com.ysh.planning.plan.domain.Category;
import com.ysh.planning.plan.dto.CategoryDto;
import com.ysh.planning.plan.dto.CreateCategoryRequest;
import com.ysh.planning.plan.repository.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public List<CategoryDto> listAvailable() {
        Long userId = UserContext.currentUserId();
        return categoryMapper.selectAvailableByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public CategoryDto create(CreateCategoryRequest req) {
        Long userId = UserContext.currentUserId();
        Category existing = categoryMapper.selectByUserIdAndName(userId, req.getName());
        if (existing != null) {
            return toDto(existing);
        }

        Category category = new Category();
        category.setUserId(userId);
        category.setName(req.getName());
        category.setIsSystem(false);
        category.setIsDeleted(false);
        category.setCreatedAt(LocalDateTime.now());
        categoryMapper.insert(category);

        return toDto(category);
    }

    public CategoryDto toDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setCategoryId(category.getId());
        dto.setName(category.getName());
        dto.setIsSystem(category.getIsSystem());
        dto.setIsDeleted(category.getIsDeleted());
        return dto;
    }
}
