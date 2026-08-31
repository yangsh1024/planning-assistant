package com.ysh.planning.common.security;

import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从当前安全上下文取得已认证用户，业务层不得信任客户端传入的用户标识。
 */
public class UserContext {

    private UserContext() {
    }

    /**
     * 读取当前请求身份。
     * <ol><li>读取上下文</li><li>校验身份</li></ol>
     *
     * @return 已认证用户标识
     * @throws BizException 当前请求未认证时抛出
     */
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return (Long) auth.getPrincipal();
    }
}
