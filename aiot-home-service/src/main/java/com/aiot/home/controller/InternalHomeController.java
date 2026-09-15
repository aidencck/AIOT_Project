package com.aiot.home.controller;

import com.aiot.common.api.Result;
import com.aiot.common.dto.home.HomeRoomRelationCheckResp;
import com.aiot.home.service.HomeCacheManager;
import com.aiot.home.service.HomeService;
import com.aiot.home.service.RoomService;
import com.aiot.home.utils.UserContext;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

/**
 * 家庭域内部接口（仅服务间调用）
 */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/homes")
@Validated
public class InternalHomeController {

    @Autowired
    private HomeCacheManager homeCacheManager;

    @Autowired
    private HomeService homeService;

    @Autowired
    private RoomService roomService;

    /**
     * 设备域跨服务鉴权使用：校验当前登录用户是否具备指定家庭角色
     */
    @GetMapping("/{homeId}/permission/check")
    public Result<Boolean> checkHomePermission(@PathVariable("homeId") @NotBlank(message = "homeId 不能为空") String homeId,
                                               @RequestParam(name = "minRole", defaultValue = "3") @Min(value = 1, message = "minRole 不能小于1") Integer minRole) {
        UserContext.UserInfo userInfo = UserContext.get();
        if (userInfo == null || userInfo.getUserId() == null || userInfo.getUserId().isBlank()) {
            return Result.success(Boolean.FALSE);
        }
        Integer role = homeCacheManager.getUserRole(homeId, userInfo.getUserId());
        return Result.success(role != null && role <= minRole);
    }

    @GetMapping("/{homeId}/relation/check")
    public Result<HomeRoomRelationCheckResp> checkHomeRoomRelation(
            @PathVariable("homeId") @NotBlank(message = "homeId 不能为空") String homeId,
            @RequestParam(name = "roomId", required = false) String roomId) {
        HomeRoomRelationCheckResp resp = new HomeRoomRelationCheckResp();
        resp.setHomeExists(homeService.existsHome(homeId));
        if (!StringUtils.hasText(roomId)) {
            resp.setRoomExists(Boolean.TRUE);
            resp.setRoomBelongsToHome(Boolean.TRUE);
            return Result.success(resp);
        }
        resp.setRoomExists(roomService.existsRoom(roomId));
        resp.setRoomBelongsToHome(roomService.roomBelongsToHome(roomId, homeId));
        return Result.success(resp);
    }
}
