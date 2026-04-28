package com.aiot.home.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.home.dto.RoomCreateReq;
import com.aiot.home.dto.RoomResp;
import com.aiot.home.entity.Room;
import com.aiot.home.repository.RoomRepository;
import com.aiot.home.service.HomeDeviceCompensationService;
import com.aiot.home.service.RoomService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomServiceImpl implements RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private HomeDeviceCompensationService homeDeviceCompensationService;

    @Override
    public String createRoom(RoomCreateReq req, String userId) {
        // 注解已在 Controller 层拦截并鉴权 (Owner/Admin)
        Room room = new Room();
        room.setHomeId(req.getHomeId());
        room.setName(req.getName());
        roomRepository.insert(room);

        return room.getId();
    }

    @Override
    public List<RoomResp> listRoomsByHomeId(String homeId, String userId) {
        // 注解已在 Controller 层拦截并鉴权 (Member 即可查看)
        LambdaQueryWrapper<Room> roomWrapper = new LambdaQueryWrapper<>();
        roomWrapper.eq(Room::getHomeId, homeId);
        List<Room> rooms = roomRepository.selectList(roomWrapper);

        return rooms.stream().map(room -> {
            RoomResp resp = new RoomResp();
            resp.setId(room.getId());
            resp.setHomeId(room.getHomeId());
            resp.setName(room.getName());
            return resp;
        }).collect(Collectors.toList());
    }

    @Override
    public void deleteRoom(String roomId, String homeId, String userId) {
        Room room = roomRepository.selectById(roomId);
        if (room == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "房间不存在");
        }

        // 二次校验资源归属，防止通过伪造 homeId 参数越权删除其他家庭房间
        if (!room.getHomeId().equals(homeId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "房间不属于当前家庭");
        }

        // 删除房间前先解绑设备的 room 引用
        homeDeviceCompensationService.unbindDevicesByRoomId(roomId);

        roomRepository.deleteById(roomId);
    }
}
