package com.ysh.planning.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.agent.domain.AgentAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentActionMapper extends BaseMapper<AgentAction> {
    @Update("UPDATE t_agent_action_audit SET status='EXECUTING', updated_at=NOW() WHERE id=#{id} AND user_id=#{userId} AND status='PENDING_CONFIRMATION' AND expires_at > NOW()")
    int claim(@Param("id") String id, @Param("userId") Long userId);

    @Update("UPDATE t_agent_action_audit SET status='CANCELLED', updated_at=NOW() WHERE id=#{id} AND user_id=#{userId} AND status='PENDING_CONFIRMATION' AND expires_at > NOW()")
    int cancel(@Param("id") String id, @Param("userId") Long userId);

    @Update("UPDATE t_agent_action_audit SET status='FAILED', result_json=#{resultJson}, updated_at=NOW() WHERE id=#{id} AND user_id=#{userId} AND status='PENDING_CONFIRMATION'")
    int failPending(@Param("id") String id, @Param("userId") Long userId, @Param("resultJson") String resultJson);

    @Update("UPDATE t_agent_action_audit SET status='EXPIRED', updated_at=NOW() WHERE id=#{id} AND user_id=#{userId} AND status='PENDING_CONFIRMATION' AND expires_at <= #{now}")
    int expirePending(@Param("id") String id, @Param("userId") Long userId, @Param("now") java.time.LocalDateTime now);
}
