package com.ysh.planning.agent.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.agent.domain.AgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
@Mapper public interface AgentMessageMapper extends BaseMapper<AgentMessage> { @Select("SELECT * FROM t_agent_message WHERE session_id=#{sessionId} AND user_id=#{userId} ORDER BY created_at ASC") List<AgentMessage> selectVisibleBySession(String sessionId, Long userId); }
