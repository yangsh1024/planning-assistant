package com.ysh.planning.webauth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.webauth.domain.WebLoginRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;

@Mapper
public interface WebLoginRequestMapper extends BaseMapper<WebLoginRequest> {
    @Select("SELECT * FROM t_web_login_request WHERE fallback_code_hash = #{fallbackCodeHash} ORDER BY created_at DESC LIMIT 1")
    WebLoginRequest selectByFallbackCodeHash(String fallbackCodeHash);

    @Update("UPDATE t_web_login_request SET user_id=#{userId}, status=#{nextStatus}, fallback_code_hash=NULL " +
            "WHERE id=#{id} AND status='PENDING' AND expires_at > #{now}")
    int resolvePending(String id, Long userId, String nextStatus, LocalDateTime now);

    @Update("UPDATE t_web_login_request SET status='CONSUMED' WHERE id=#{id} AND status='APPROVED' AND expires_at > #{now}")
    int consumeApproved(String id, LocalDateTime now);

    @Update("UPDATE t_web_login_request SET status='EXPIRED', fallback_code_hash=NULL WHERE status IN ('PENDING','APPROVED') AND expires_at <= #{now}")
    int expireOutstanding(LocalDateTime now);

    @Delete("DELETE FROM t_web_login_request WHERE status IN ('REJECTED','EXPIRED','CONSUMED') AND created_at < #{before}")
    int deleteFinishedBefore(LocalDateTime before);
}
