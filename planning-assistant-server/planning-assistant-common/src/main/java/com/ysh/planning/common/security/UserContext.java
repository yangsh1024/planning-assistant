package com.ysh.planning.common.security;

import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserContext {

    private UserContext() {
    }

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return (Long) auth.getPrincipal();
    }
}
