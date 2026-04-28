package com.aiot.home.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.home.dto.LoginReq;
import com.aiot.home.dto.LoginResp;
import com.aiot.home.dto.RegisterReq;
import com.aiot.home.entity.User;
import com.aiot.home.repository.UserRepository;
import com.aiot.home.service.UserService;
import com.aiot.home.utils.JwtUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserServiceImpl implements UserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public LoginResp login(LoginReq req) {
        // 查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, req.getPhone());
        User user = userRepository.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户不存在");
        }

        // 优先使用 BCrypt 校验；若是历史 MD5 账号，登录成功后自动升级为 BCrypt
        String rawPassword = req.getPassword();
        boolean passwordMatched;
        if (user.getPassword() != null && user.getPassword().startsWith("$2")) {
            passwordMatched = PASSWORD_ENCODER.matches(rawPassword, user.getPassword());
        } else {
            String md5Password = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
            passwordMatched = md5Password.equals(user.getPassword());
            if (passwordMatched) {
                user.setPassword(PASSWORD_ENCODER.encode(rawPassword));
                userRepository.updateById(user);
            }
        }

        if (!passwordMatched) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "密码错误");
        }

        // 生成 Token
        String token = jwtUtils.generateToken(user.getId(), user.getPhone());

        LoginResp resp = new LoginResp();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setNickname(user.getNickname());
        return resp;
    }

    @Override
    public void register(RegisterReq req) {
        // 检查手机号是否已注册
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, req.getPhone());
        if (userRepository.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "手机号已被注册");
        }

        User user = new User();
        user.setPhone(req.getPhone());
        user.setNickname(req.getNickname());
        user.setPassword(PASSWORD_ENCODER.encode(req.getPassword()));

        userRepository.insert(user);
    }
}
