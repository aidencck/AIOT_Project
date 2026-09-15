package com.aiot.home.controller;

import com.aiot.home.dto.LoginReq;
import com.aiot.home.dto.LoginResp;
import com.aiot.home.dto.RegisterReq;
import com.aiot.home.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户接口", description = "用户注册与登录相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "用户注册", description = "注册新用户")
    @ApiResponse(responseCode = "201", description = "创建成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Void register(@Valid @RequestBody RegisterReq req) {
        userService.register(req);
        return null;
    }

    @Operation(summary = "用户登录", description = "用户登录并获取访问令牌")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/login")
    public LoginResp login(@Valid @RequestBody LoginReq req) {
        return userService.login(req);
    }
}
