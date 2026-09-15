package com.aiot.home.controller;

import com.aiot.home.annotation.RequireHomeRole;
import com.aiot.home.dto.RoomCreateReq;
import com.aiot.home.dto.RoomResp;
import com.aiot.home.dto.RoomUpdateReq;
import com.aiot.home.service.RoomService;
import com.aiot.home.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
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
 * 房间接口
 */
@RestController
@RequestMapping("/api/v1/rooms")
@Validated
@Tag(name = "房间接口", description = "房间管理相关接口")
public class RoomController {

    @Autowired
    private RoomService roomService;

    /**
     * 创建房间
     */
    @Operation(summary = "创建房间", description = "在指定家庭下创建房间，成功后返回房间ID")
    @ApiResponse(responseCode = "201", description = "创建成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping
    @RequireHomeRole(minRole = 2, homeIdParam = "homeId") // 至少需要 Admin(2)
    @ResponseStatus(HttpStatus.CREATED)
    public String createRoom(@Valid @RequestBody RoomCreateReq req) {
        String userId = UserContext.get().getUserId();
        return roomService.createRoom(req, userId);
    }

    /**
     * 查询家庭下的所有房间
     */
    @Operation(summary = "查询家庭下的所有房间", description = "查询指定家庭下的所有房间")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping
    @RequireHomeRole(minRole = 3, homeIdParam = "homeId") // Member(3) 即可查看
    public List<RoomResp> listRooms(@RequestParam @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId) {
        String userId = UserContext.get().getUserId();
        return roomService.listRoomsByHomeId(homeId, userId);
    }

    /**
     * 更新房间
     */
    @Operation(summary = "更新房间", description = "更新指定房间的名称与类型")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "房间不存在")
    @PutMapping("/{roomId}")
    @RequireHomeRole(minRole = 2, homeIdParam = "homeId") // 至少需要 Admin(2)
    public Void updateRoom(@PathVariable @NotBlank(message = "roomId 不能为空") @Parameter(description = "房间ID") String roomId,
                           @RequestParam @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId,
                           @Valid @RequestBody RoomUpdateReq req) {
        String userId = UserContext.get().getUserId();
        roomService.updateRoom(roomId, homeId, req, userId);
        return null;
    }

    /**
     * 删除房间
     * 注意：由于 URL 只有 roomId，我们在查询参数中强求传入 homeId 以供 AOP 鉴权
     */
    @Operation(summary = "删除房间", description = "删除指定房间")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "房间不存在")
    @DeleteMapping("/{roomId}")
    @RequireHomeRole(minRole = 2, homeIdParam = "homeId") // 至少需要 Admin(2)
    public Void deleteRoom(@PathVariable @NotBlank(message = "roomId 不能为空") @Parameter(description = "房间ID") String roomId,
                           @RequestParam @NotBlank(message = "homeId 不能为空") @Parameter(description = "家庭ID") String homeId) {
        String userId = UserContext.get().getUserId();
        roomService.deleteRoom(roomId, homeId, userId);
        return null;
    }
}
