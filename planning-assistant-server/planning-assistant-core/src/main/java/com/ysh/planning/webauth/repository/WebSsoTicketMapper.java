package com.ysh.planning.webauth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ysh.planning.webauth.domain.WebSsoTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;

@Mapper
public interface WebSsoTicketMapper extends BaseMapper<WebSsoTicket> {
    @Select("SELECT * FROM t_web_sso_ticket WHERE ticket_hash = #{ticketHash} LIMIT 1")
    WebSsoTicket selectByTicketHash(String ticketHash);

    @Update("UPDATE t_web_sso_ticket SET consumed_at=#{consumedAt} " +
            "WHERE id=#{id} AND consumed_at IS NULL AND expires_at > #{consumedAt}")
    int consume(String id, LocalDateTime consumedAt);

    @Delete("DELETE FROM t_web_sso_ticket WHERE (consumed_at IS NOT NULL OR expires_at <= #{before}) AND created_at < #{before}")
    int deleteStaleBefore(LocalDateTime before);
}
