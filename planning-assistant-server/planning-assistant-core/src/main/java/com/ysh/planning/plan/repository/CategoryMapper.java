package com.ysh.planning.plan.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.plan.domain.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    @Select("SELECT * FROM t_category WHERE (user_id = 0 OR user_id = #{userId}) AND is_deleted = false ORDER BY is_system DESC, id ASC")
    List<Category> selectAvailableByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM t_category WHERE (user_id = 0 OR user_id = #{userId}) AND name = #{name} AND is_deleted = false LIMIT 1")
    Category selectByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);
}
