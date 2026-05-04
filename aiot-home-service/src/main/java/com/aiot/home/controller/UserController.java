package com.aiot.home.controller;

import com.aiot.home.dto.LoginReq;
import com.aiot.home.dto.LoginResp;
import com.aiot.home.dto.RegisterReq;
import com.aiot.home.service.UserService;
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
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Void register(@Valid @RequestBody RegisterReq req) {
        userService.register(req);
        return null;
    }

    @PostMapping("/login")
    public LoginResp login(@Valid @RequestBody LoginReq req) {
        return userService.login(req);
    }
}
