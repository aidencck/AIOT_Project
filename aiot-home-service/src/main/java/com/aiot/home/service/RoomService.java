package com.aiot.home.service;

import com.aiot.home.dto.RoomCreateReq;
import com.aiot.home.dto.RoomResp;

import java.util.List;

public interface RoomService {
    
    String createRoom(RoomCreateReq req, String userId);

    List<RoomResp> listRoomsByHomeId(String homeId, String userId);

    void deleteRoom(String roomId, String homeId, String userId);
}
