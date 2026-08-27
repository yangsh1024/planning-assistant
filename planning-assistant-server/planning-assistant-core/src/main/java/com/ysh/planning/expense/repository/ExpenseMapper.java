package com.ysh.planning.expense.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.expense.domain.Expense;
import com.ysh.planning.expense.dto.ExpenseStatRawDto;
import com.ysh.planning.expense.dto.ExpenseWithCategoryDto;
import com.ysh.planning.expense.dto.TrendRawDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExpenseMapper extends BaseMapper<Expense> {

    @Select("<script>" +
            "SELECT e.id, e.user_id, e.category_id, c.name AS category_name, " +
            "e.amount, e.expense_date, e.note, e.created_at, e.updated_at " +
            "FROM t_expense e " +
            "LEFT JOIN t_category c ON e.category_id = c.id " +
            "WHERE e.user_id = #{userId} " +
            "AND e.is_deleted = false " +
            "AND DATE_FORMAT(e.expense_date, '%Y-%m') = #{yearMonth} " +
            "<if test='categoryId != null'>AND e.category_id = #{categoryId} </if>" +
            "ORDER BY e.expense_date DESC, e.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<ExpenseWithCategoryDto> listByMonth(@Param("userId") Long userId,
                                              @Param("yearMonth") String yearMonth,
                                              @Param("categoryId") Long categoryId,
                                              @Param("limit") int limit,
                                              @Param("offset") long offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM t_expense " +
            "WHERE user_id = #{userId} " +
            "AND is_deleted = false " +
            "AND DATE_FORMAT(expense_date, '%Y-%m') = #{yearMonth} " +
            "<if test='categoryId != null'>AND category_id = #{categoryId}</if>" +
            "</script>")
    long countByMonth(@Param("userId") Long userId,
                      @Param("yearMonth") String yearMonth,
                      @Param("categoryId") Long categoryId);

    @Select("SELECT e.category_id, c.name AS category_name, " +
            "SUM(e.amount) AS total, COUNT(*) AS count " +
            "FROM t_expense e " +
            "LEFT JOIN t_category c ON e.category_id = c.id " +
            "WHERE e.user_id = #{userId} " +
            "AND e.is_deleted = false " +
            "AND DATE_FORMAT(e.expense_date, '%Y-%m') = #{yearMonth} " +
            "GROUP BY e.category_id, c.name " +
            "ORDER BY total DESC")
    List<ExpenseStatRawDto> statsByMonth(@Param("userId") Long userId,
                                          @Param("yearMonth") String yearMonth);

    @Select("<script>" +
            "SELECT DATE_FORMAT(expense_date, '%Y-%m') AS year_month, SUM(amount) AS total " +
            "FROM t_expense " +
            "WHERE user_id = #{userId} " +
            "AND is_deleted = false " +
            "AND DATE_FORMAT(expense_date, '%Y-%m') IN " +
            "<foreach item='m' collection='months' open='(' separator=',' close=')'>" +
            "#{m}" +
            "</foreach> " +
            "GROUP BY DATE_FORMAT(expense_date, '%Y-%m')" +
            "</script>")
    List<TrendRawDto> trendByMonths(@Param("userId") Long userId,
                                     @Param("months") List<String> months);

    @Select("SELECT e.id, e.user_id, e.category_id, c.name AS category_name, " +
            "e.amount, e.expense_date, e.note, e.created_at, e.updated_at " +
            "FROM t_expense e " +
            "LEFT JOIN t_category c ON e.category_id = c.id " +
            "WHERE e.id = #{id} AND e.is_deleted = false")
    ExpenseWithCategoryDto selectWithCategoryById(@Param("id") Long id);
}
