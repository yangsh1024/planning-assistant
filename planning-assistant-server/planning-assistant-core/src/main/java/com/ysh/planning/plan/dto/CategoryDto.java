package com.ysh.planning.plan.dto;

import lombok.Data;

@Data
public class CategoryDto {

    private Long categoryId;
    private String name;
    private Boolean isSystem;
    private Boolean isDeleted;
}
