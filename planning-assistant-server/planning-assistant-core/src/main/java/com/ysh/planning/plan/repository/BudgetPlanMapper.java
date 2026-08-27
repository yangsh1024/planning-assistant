package com.ysh.planning.plan.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.plan.domain.BudgetPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BudgetPlanMapper extends BaseMapper<BudgetPlan> {

    @Select("SELECT * FROM t_budget_plan WHERE user_id = #{userId} AND `year_month` = #{yearMonth} LIMIT 1")
    BudgetPlan selectByUserIdAndYearMonth(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);

    @Select("SELECT * FROM t_budget_plan WHERE user_id = #{userId} AND `year_month` = #{yearMonth} LIMIT 1 FOR UPDATE")
    BudgetPlan selectByUserIdAndYearMonthForUpdate(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);

    @Select("SELECT COUNT(*) FROM t_budget_plan WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);
}
