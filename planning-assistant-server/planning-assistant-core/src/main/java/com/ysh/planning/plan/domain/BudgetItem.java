package com.ysh.planning.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 表示月度预算方案中某一分类的额度与展示顺序。 */
@Data
@TableName("t_budget_item")
public class BudgetItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long categoryId;
    private BigDecimal amount;
    private Integer sortOrder;
}
