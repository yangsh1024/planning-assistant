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

/** 管理用户可选分类，并让重复创建同名分类保持稳定结果。 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    /**
     * 查询当前用户可用的系统分类和自定义分类。
     *
     * <ol>
     *     <li>读取当前登录用户。</li>
     *     <li>查询其可选分类并转换为接口对象。</li>
     * </ol>
     *
     * @return 可用分类列表
     */
    public List<CategoryDto> listAvailable() {
        Long userId = UserContext.currentUserId();
        return categoryMapper.selectAvailableByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 创建当前用户的自定义分类。
     *
     * <ol>
     *     <li>按用户和名称查找已有分类。</li>
     *     <li>存在时直接返回，避免产生语义重复的数据。</li>
     *     <li>不存在时保存新的自定义分类。</li>
     * </ol>
     *
     * @param req 分类名称
     * @return 已存在或新建的分类
     */
    public CategoryDto create(CreateCategoryRequest req) {
        Long userId = UserContext.currentUserId();
        Category existing = categoryMapper.selectByUserIdAndName(userId, req.getName());
        // 同名分类直接复用，避免预算和开支出现语义相同的重复项。
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

    /**
     * 将分类持久化对象转换为接口对象。
     *
     * @param category 分类实体
     * @return 分类接口对象
     */
    public CategoryDto toDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setCategoryId(category.getId());
        dto.setName(category.getName());
        dto.setIsSystem(category.getIsSystem());
        dto.setIsDeleted(category.getIsDeleted());
        return dto;
    }
}
