package com.aiot.home.controller;

import com.aiot.home.service.HomeCacheManager;
import com.aiot.home.utils.UserContext;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家庭域内部接口（仅服务间调用）
 */
@RestController
@RequestMapping("/api/v1/internal/homes")
@Validated
public class InternalHomeController {

    @Autowired
    private HomeCacheManager homeCacheManager;

    /**
     * 设备域跨服务鉴权使用：校验当前登录用户是否具备指定家庭角色
     */
    @GetMapping("/{homeId}/permission/check")
    public Boolean checkHomePermission(@PathVariable("homeId") @NotBlank(message = "homeId 不能为空") String homeId,
                                       @RequestParam(name = "minRole", defaultValue = "3") @Min(value = 1, message = "minRole 不能小于1") Integer minRole) {
        UserContext.UserInfo userInfo = UserContext.get();
        if (userInfo == null || userInfo.getUserId() == null || userInfo.getUserId().isBlank()) {
            return false;
        }
        Integer role = homeCacheManager.getUserRole(homeId, userInfo.getUserId());
        return role != null && role <= minRole;
    }
}
