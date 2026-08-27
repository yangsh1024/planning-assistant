package com.ysh.planning.agent.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.agent.domain.AgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper public interface AgentSessionMapper extends BaseMapper<AgentSession> { @Select("SELECT * FROM t_agent_session WHERE user_id=#{userId} ORDER BY updated_at DESC") List<AgentSession> selectByUserId(Long userId); }
