package com.ysh.planning.common.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        jwtUtil.validateSecret();
    }

    @Test
    void keepsMiniappAndWebTokensPurposeBound() {
        String miniapp = jwtUtil.generateToken(7L);
        String web = jwtUtil.generateWebToken(7L);

        assertThat(jwtUtil.parseUserId(miniapp)).isEqualTo(7L);
        assertThat(jwtUtil.parseWebUserId(web)).isEqualTo(7L);
        assertThatThrownBy(() -> jwtUtil.parseUserId(web)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtUtil.parseWebUserId(miniapp)).isInstanceOf(JwtException.class);
    }
}
