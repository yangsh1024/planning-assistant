package com.ysh.planning.plan.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.plan.domain.BudgetItem;
import com.ysh.planning.plan.dto.BudgetItemWithNameDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BudgetItemMapper extends BaseMapper<BudgetItem> {

    @Select("SELECT bi.*, c.name AS category_name FROM t_budget_item bi " +
            "LEFT JOIN t_category c ON bi.category_id = c.id " +
            "WHERE bi.plan_id = #{planId} ORDER BY bi.sort_order ASC, bi.id ASC")
    List<BudgetItemWithNameDto> selectWithNameByPlanId(@Param("planId") Long planId);
}
