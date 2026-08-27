package com.ysh.planning.plan.controller;

import com.ysh.planning.common.response.Result;
import com.ysh.planning.plan.dto.CategoryDto;
import com.ysh.planning.plan.dto.CreateCategoryRequest;
import com.ysh.planning.plan.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public Result<List<CategoryDto>> listAvailable() {
        return Result.ok(categoryService.listAvailable());
    }

    @PostMapping
    public Result<CategoryDto> create(@Valid @RequestBody CreateCategoryRequest req) {
        return Result.ok(categoryService.create(req));
    }
}
