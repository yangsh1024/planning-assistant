package com.ysh.planning.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_budget_plan")
/** 表示用户在某个月份保存的一份整体预算方案。 */
public class BudgetPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    @TableField("`year_month`")
    private String yearMonth;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
