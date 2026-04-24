package com.aiot.home.controller;

import com.aiot.home.annotation.RequireHomeRole;
import com.aiot.home.dto.RoomCreateReq;
import com.aiot.home.dto.RoomResp;
import com.aiot.home.service.RoomService;
import com.aiot.home.utils.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 房间接口
 */
@RestController
@RequestMapping("/api/v1/rooms")
@Validated
public class RoomController {

    @Autowired
    private RoomService roomService;

    /**
     * 创建房间
     */
    @PostMapping
    @RequireHomeRole(minRole = 2, homeIdParam = "homeId") // 至少需要 Admin(2)
    public String createRoom(@Valid @RequestBody RoomCreateReq req) {
        String userId = UserContext.get().getUserId();
        return roomService.createRoom(req, userId);
    }

    /**
     * 查询家庭下的所有房间
     */
    @GetMapping
    @RequireHomeRole(minRole = 3, homeIdParam = "homeId") // Member(3) 即可查看
    public List<RoomResp> listRooms(@RequestParam @NotBlank(message = "homeId 不能为空") String homeId) {
        String userId = UserContext.get().getUserId();
        return roomService.listRoomsByHomeId(homeId, userId);
    }

    /**
     * 删除房间
     * 注意：由于 URL 只有 roomId，我们在查询参数中强求传入 homeId 以供 AOP 鉴权
     */
    @DeleteMapping("/{roomId}")
    @RequireHomeRole(minRole = 2, homeIdParam = "homeId") // 至少需要 Admin(2)
    public Void deleteRoom(@PathVariable @NotBlank(message = "roomId 不能为空") String roomId,
                           @RequestParam @NotBlank(message = "homeId 不能为空") String homeId) {
        String userId = UserContext.get().getUserId();
        roomService.deleteRoom(roomId, homeId, userId);
        return null;
    }
}
