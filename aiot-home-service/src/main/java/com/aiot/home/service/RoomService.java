package com.aiot.home.service;

import com.aiot.home.dto.RoomCreateReq;
import com.aiot.home.dto.RoomResp;
import com.aiot.home.dto.RoomUpdateReq;

import java.util.List;

public interface RoomService {
    
    String createRoom(RoomCreateReq req, String userId);

    List<RoomResp> listRoomsByHomeId(String homeId, String userId);

    void updateRoom(String roomId, String homeId, RoomUpdateReq req, String userId);

    void deleteRoom(String roomId, String homeId, String userId);

    boolean existsRoom(String roomId);

    boolean roomBelongsToHome(String roomId, String homeId);

    int deleteRoomsByHomeId(String homeId);
}
