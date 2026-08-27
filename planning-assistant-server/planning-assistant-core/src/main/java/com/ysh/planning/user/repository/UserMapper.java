package com.ysh.planning.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM t_user WHERE openid = #{openid} LIMIT 1")
    User selectByOpenid(String openid);
}
