package com.ysh.planning.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 表示内置或用户自定义的记账分类，并保留软删除状态供历史数据使用。 */
@Data
@TableName("t_category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private Boolean isSystem;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
}
