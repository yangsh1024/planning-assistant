package com.ysh.planning.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "科目名称不能为空")
    @Size(min = 1, max = 20, message = "科目名称长度必须在1-20之间")
    private String name;
}
