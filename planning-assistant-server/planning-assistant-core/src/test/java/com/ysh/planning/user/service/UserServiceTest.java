package com.ysh.planning.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysh.planning.common.exception.BizException;
import com.ysh.planning.common.security.JwtUtil;
import com.ysh.planning.user.domain.User;
import com.ysh.planning.user.dto.UpdateProfileRequest;
import com.ysh.planning.user.repository.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtUtil jwtUtil;

    private UserService userService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        userService = new UserService(userMapper, jwtUtil, new ObjectMapper());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateProfileRejectsAvatarOutsideBuiltInCatalog() {
        User user = new User();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setAvatar("https://example.com/avatar.png");

        assertThrows(BizException.class, () -> userService.updateProfile(request));

        verify(userMapper, never()).update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
