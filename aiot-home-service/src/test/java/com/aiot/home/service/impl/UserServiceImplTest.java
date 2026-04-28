package com.aiot.home.service.impl;

import com.aiot.common.exception.BusinessException;
import com.aiot.home.dto.LoginReq;
import com.aiot.home.dto.LoginResp;
import com.aiot.home.dto.RegisterReq;
import com.aiot.home.entity.User;
import com.aiot.home.repository.UserRepository;
import com.aiot.home.utils.JwtUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void login_shouldUpgradeMd5PasswordAndReturnToken() {
        LoginReq req = new LoginReq();
        req.setPhone("13800138000");
        req.setPassword("123456");

        User user = new User();
        user.setId("u-1");
        user.setPhone(req.getPhone());
        user.setNickname("alice");
        user.setPassword(DigestUtils.md5DigestAsHex(req.getPassword().getBytes(StandardCharsets.UTF_8)));

        when(userRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(jwtUtils.generateToken("u-1", req.getPhone())).thenReturn("mock-token");

        LoginResp resp = userService.login(req);

        assertEquals("mock-token", resp.getToken());
        assertEquals("u-1", resp.getUserId());
        assertEquals("alice", resp.getNickname());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).updateById(captor.capture());
        assertNotNull(captor.getValue().getPassword());
        assertTrue(captor.getValue().getPassword().startsWith("$2"));
    }

    @Test
    void login_shouldThrowWhenPasswordIncorrect() {
        LoginReq req = new LoginReq();
        req.setPhone("13800138000");
        req.setPassword("wrong-pass");

        User user = new User();
        user.setId("u-1");
        user.setPhone(req.getPhone());
        user.setPassword("$2a$10$0OcYqLaXx3Di8Q2REwW65eR0r4Vjzi10BGSAdoo6gWQBaIjXImQxG");

        when(userRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThrows(BusinessException.class, () -> userService.login(req));
        verify(userRepository, never()).updateById(any(User.class));
    }

    @Test
    void register_shouldRejectDuplicatePhone() {
        RegisterReq req = new RegisterReq();
        req.setPhone("13800138000");
        req.setPassword("123456");
        req.setNickname("bob");

        when(userRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> userService.register(req));
        verify(userRepository, never()).insert(any(User.class));
    }

    @Test
    void register_shouldHashPasswordBeforeInsert() {
        RegisterReq req = new RegisterReq();
        req.setPhone("13800138000");
        req.setPassword("123456");
        req.setNickname("bob");

        when(userRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals(req.getPhone(), inserted.getPhone());
        assertEquals(req.getNickname(), inserted.getNickname());
        assertNotNull(inserted.getPassword());
        assertTrue(inserted.getPassword().startsWith("$2"));
    }
}
