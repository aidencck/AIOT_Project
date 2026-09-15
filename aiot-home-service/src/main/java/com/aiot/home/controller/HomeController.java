package com.aiot.home.controller;

import com.aiot.home.annotation.RequireHomeRole;
import com.aiot.home.dto.HomeCreateReq;
import com.aiot.home.dto.HomeMemberAddReq;
import com.aiot.home.dto.HomeMemberResp;
import com.aiot.home.dto.HomeMemberRoleUpdateReq;
import com.aiot.home.dto.HomeResp;
import com.aiot.home.dto.HomeUpdateReq;
import com.aiot.home.service.HomeCacheManager;
import com.aiot.home.service.HomeService;
import com.aiot.home.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * 家庭接口
 */
@RestController
@RequestMapping("/api/v1/homes")
@Validated
@Tag(name = "家庭接口", description = "家庭管理相关接口")
public class HomeController {

    @Autowired
    private HomeService homeService;
    @Autowired
    private HomeCacheManager homeCacheManager;

    /**
     * 创建家庭
     */
    @Operation(summary = "创建家庭", description = "创建家庭，成功后返回家庭ID")
    @ApiResponse(responseCode = "201", description = "创建成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String createHome(@Valid @RequestBody HomeCreateReq req) {
        String userId = UserContext.get().getUserId();
        return homeService.createHome(req, userId);
    }

    /**
     * 获取当前用户的家庭列表
     */
    @Operation(summary = "获取当前用户的家庭列表", description = "查询当前登录用户拥有的全部家庭")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping
    public List<HomeResp> listMyHomes() {
        String userId = UserContext.get().getUserId();
        return homeService.listUserHomes(userId);
    }

    /**
     * 删除家庭
     */
    @Operation(summary = "删除家庭", description = "删除指定家庭（仅 Owner 可操作）")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "家庭不存在")
    @DeleteMapping("/{homeId}")
    @RequireHomeRole(minRole = 1, homeIdParam = "homeId") // 仅 Owner (Role=1) 可删除
    public Void deleteHome(@PathVariable @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId) {
        String userId = UserContext.get().getUserId();
        homeService.deleteHome(homeId, userId);
        return null;
    }

    /**
     * 查询家庭成员列表
     */
    @Operation(summary = "查询家庭成员列表", description = "查询指定家庭下的所有成员")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/{homeId}/members")
    @RequireHomeRole(minRole = 3, homeIdParam = "homeId")
    public List<HomeMemberResp> listHomeMembers(@PathVariable @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId) {
        return homeService.listHomeMembers(homeId);
    }

    /**
     * 修改家庭信息（名称/位置）
     */
    @Operation(summary = "修改家庭信息", description = "修改家庭名称或位置（Owner/管理员可操作）")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "403", description = "无权限")
    @ApiResponse(responseCode = "404", description = "家庭不存在")
    @PutMapping("/{homeId}")
    @RequireHomeRole(minRole = 2, homeIdParam = "homeId")
    public Void updateHome(@PathVariable @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId,
                           @Valid @RequestBody HomeUpdateReq req) {
        String userId = UserContext.get().getUserId();
        homeService.updateHome(homeId, req, userId);
        return null;
    }

    /**
     * 添加家庭成员
     */
    @Operation(summary = "添加家庭成员", description = "向指定家庭添加成员")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "家庭不存在")
    @PostMapping("/{homeId}/members")
    @RequireHomeRole(minRole = 2, homeIdParam = "homeId")
    public Void addHomeMember(@PathVariable @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId,
                              @Valid @RequestBody HomeMemberAddReq req) {
        String userId = UserContext.get().getUserId();
        homeService.addHomeMember(homeId, req, userId);
        return null;
    }

    /**
     * 更新家庭成员角色（仅 Owner）
     */
    @Operation(summary = "更新家庭成员角色", description = "更新指定家庭成员的角色（仅 Owner 可操作）")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "家庭或成员不存在")
    @PutMapping("/{homeId}/members/{userId}/role")
    @RequireHomeRole(minRole = 1, homeIdParam = "homeId")
    public Void updateHomeMemberRole(@PathVariable @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId,
                                     @PathVariable @NotBlank(message = "userId 不能为空") @Parameter(description = "用户ID") String userId,
                                     @Valid @RequestBody HomeMemberRoleUpdateReq req) {
        String operatorUserId = UserContext.get().getUserId();
        homeService.updateHomeMemberRole(homeId, userId, req, operatorUserId);
        return null;
    }

    /**
     * 移除家庭成员（仅 Owner）
     */
    @Operation(summary = "移除家庭成员", description = "移除指定家庭成员（仅 Owner 可操作）")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "家庭或成员不存在")
    @DeleteMapping("/{homeId}/members/{userId}")
    @RequireHomeRole(minRole = 1, homeIdParam = "homeId")
    public Void removeHomeMember(@PathVariable @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId,
                                 @PathVariable @NotBlank(message = "userId 不能为空") @Parameter(description = "用户ID") String userId) {
        String operatorUserId = UserContext.get().getUserId();
        homeService.removeHomeMember(homeId, userId, operatorUserId);
        return null;
    }

    /**
     * 设备域跨服务鉴权使用：校验当前登录用户是否具备指定家庭角色
     */
    @Operation(summary = "校验家庭权限", description = "校验当前登录用户是否具备指定家庭角色")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/{homeId}/permission/check")
    public Boolean checkHomePermission(@PathVariable @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId,
                                       @RequestParam(defaultValue = "3") @Min(value = 1, message = "minRole 不能小于1") @Parameter(description = "最小角色等级") Integer minRole) {
        String userId = UserContext.get().getUserId();
        Integer role = homeCacheManager.getUserRole(homeId, userId);
        return role != null && role <= minRole;
    }
}
