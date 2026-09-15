package com.aiot.common.dto.home;

import lombok.Data;

@Data
public class HomeRoomRelationCheckResp {
    private Boolean homeExists;
    private Boolean roomExists;
    private Boolean roomBelongsToHome;
}
