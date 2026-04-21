package com.aiot.home.controller;

import com.aiot.home.annotation.RequireHomeRole;
import com.aiot.home.dto.HomeCreateReq;
import com.aiot.home.dto.HomeMemberAddReq;
import com.aiot.home.dto.HomeMemberResp;
import com.aiot.home.dto.HomeMemberRoleUpdateReq;
import com.aiot.home.dto.HomeResp;
import com.aiot.home.service.HomeCacheManager;
import com.aiot.home.service.HomeService;
import com.aiot.home.utils.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 家庭接口
 */
@RestController
@RequestMapping("/api/v1/homes")
@Validated
public class HomeController {

    @Autowired
    private HomeService homeService;
    @Autowired
    private HomeCacheManager homeCacheManager;

    /**
     * 创建家庭
     */
    @PostMapping
    public String createHome(@Valid @RequestBody HomeCreateReq req) {
        String userId = UserContext.get().getUserId();
        return homeService.createHome(req, userId);
    }

    /**
     * 获取当前用户的家庭列表
     */
    @GetMapping
    public List<HomeResp> listMyHomes() {
        String userId = UserContext.get().getUserId();
        return homeService.listUserHomes(userId);
    }

    /**
     * 删除家庭
     */
    @DeleteMapping("/{homeId}")
    @RequireHomeRole(minRole = 1, homeIdParam = "homeId") // 仅 Owner (Role=1) 可删除
    public Void deleteHome(@PathVariable @NotBlank(message = "homeId 不能为空") String homeId) {
        String userId = UserContext.get().getUserId();
        homeService.deleteHome(homeId, userId);
        return null;
    }

    /**
     * 查询家庭成员列表
     */
    @GetMapping("/{homeId}/members")
    @RequireHomeRole(minRole = 3, homeIdParam = "homeId")
    public List<HomeMemberResp> listHomeMembers(@PathVariable @NotBlank(message = "homeId 不能为空") String homeId) {
        return homeService.listHomeMembers(homeId);
    }

    /**
     * 添加家庭成员
     */
    @PostMapping("/{homeId}/members")
    @RequireHomeRole(minRole = 2, homeIdParam = "homeId")
    public Void addHomeMember(@PathVariable @NotBlank(message = "homeId 不能为空") String homeId,
                              @Valid @RequestBody HomeMemberAddReq req) {
        String userId = UserContext.get().getUserId();
        homeService.addHomeMember(homeId, req, userId);
        return null;
    }

    /**
     * 更新家庭成员角色（仅 Owner）
     */
    @PutMapping("/{homeId}/members/{userId}/role")
    @RequireHomeRole(minRole = 1, homeIdParam = "homeId")
    public Void updateHomeMemberRole(@PathVariable @NotBlank(message = "homeId 不能为空") String homeId,
                                     @PathVariable @NotBlank(message = "userId 不能为空") String userId,
                                     @Valid @RequestBody HomeMemberRoleUpdateReq req) {
        String operatorUserId = UserContext.get().getUserId();
        homeService.updateHomeMemberRole(homeId, userId, req, operatorUserId);
        return null;
    }

    /**
     * 移除家庭成员（仅 Owner）
     */
    @DeleteMapping("/{homeId}/members/{userId}")
    @RequireHomeRole(minRole = 1, homeIdParam = "homeId")
    public Void removeHomeMember(@PathVariable @NotBlank(message = "homeId 不能为空") String homeId,
                                 @PathVariable @NotBlank(message = "userId 不能为空") String userId) {
        String operatorUserId = UserContext.get().getUserId();
        homeService.removeHomeMember(homeId, userId, operatorUserId);
        return null;
    }

    /**
     * 设备域跨服务鉴权使用：校验当前登录用户是否具备指定家庭角色
     */
    @GetMapping("/{homeId}/permission/check")
    public Boolean checkHomePermission(@PathVariable @NotBlank(message = "homeId 不能为空") String homeId,
                                       @RequestParam(defaultValue = "3") @Min(value = 1, message = "minRole 不能小于1") Integer minRole) {
        String userId = UserContext.get().getUserId();
        Integer role = homeCacheManager.getUserRole(homeId, userId);
        return role != null && role <= minRole;
    }
}
