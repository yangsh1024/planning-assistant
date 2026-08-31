package com.ysh.planning.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** 声明小程序与 Web 两条认证链路的访问边界和过滤顺序。 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final WebCookieAuthenticationFilter webCookieAuthenticationFilter;
    private final WebCsrfFilter webCsrfFilter;

    @Bean
    /**
     * 构建无状态的接口安全策略。
     * <ol><li>划分端点</li><li>装配认证</li><li>统一拒绝</li></ol>
     * @param http 安全配置构建器
     * @return 生效的过滤器链
     * @throws Exception 安全链无法构建时抛出
     */
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/user/login", "/api/web-auth/requests", "/api/web-auth/requests/*/status", "/api/web-auth/requests/*/qr",
                                "/api/web-auth/requests/*/exchange", "/api/web-auth/sso/exchange").permitAll()
                        .requestMatchers("/api/agent/**").hasRole("WEB")
                        .requestMatchers("/api/web-auth/requests/*/preview", "/api/web-auth/requests/*/approve", "/api/web-auth/requests/*/reject",
                                "/api/web-auth/fallback/resolve", "/api/web-auth/miniapp-links").hasRole("MINIAPP")
                        .anyRequest().hasRole("MINIAPP")
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(webCookieAuthenticationFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(webCsrfFilter, WebCookieAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setContentType("application/json;charset=UTF-8");
                            res.setStatus(401);
                            res.getWriter().write("{\"code\":401,\"message\":\"未认证\",\"data\":null}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setContentType("application/json;charset=UTF-8");
                            res.setStatus(403);
                            res.getWriter().write("{\"code\":403,\"message\":\"无权限\",\"data\":null}");
                        })
                );
        return http.build();
    }
}
