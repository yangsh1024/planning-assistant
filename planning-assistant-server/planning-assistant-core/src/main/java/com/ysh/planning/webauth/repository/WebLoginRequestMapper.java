package com.ysh.planning.webauth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.webauth.domain.WebLoginRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface WebLoginRequestMapper extends BaseMapper<WebLoginRequest> {
    @Update("UPDATE t_web_login_request SET user_id=#{userId}, status=#{nextStatus} " +
            "WHERE id=#{id} AND status='PENDING' AND expires_at > #{now}")
    int resolvePending(String id, Long userId, String nextStatus, LocalDateTime now);

    @Update("UPDATE t_web_login_request SET status='CONSUMED' WHERE id=#{id} AND status='APPROVED' AND expires_at > #{now}")
    int consumeApproved(String id, LocalDateTime now);

    @Select("SELECT id, user_id, browser_proof_hash, fallback_code_hash AS login_code_hash, device_label, status, expires_at, created_at " +
            "FROM t_web_login_request WHERE fallback_code_hash=#{loginCodeHash}")
    WebLoginRequest selectByLoginCodeHash(String loginCodeHash);

    @Update("UPDATE t_web_login_request SET status='EXPIRED' WHERE status IN ('PENDING','APPROVED') AND expires_at <= #{now}")
    int expireOutstanding(LocalDateTime now);

    @Delete("DELETE FROM t_web_login_request WHERE status IN ('REJECTED','EXPIRED','CONSUMED') AND created_at < #{before}")
    int deleteFinishedBefore(LocalDateTime before);
}
