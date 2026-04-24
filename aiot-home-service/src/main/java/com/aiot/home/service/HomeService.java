package com.aiot.home.service;

import com.aiot.home.dto.HomeCreateReq;
import com.aiot.home.dto.HomeMemberAddReq;
import com.aiot.home.dto.HomeMemberResp;
import com.aiot.home.dto.HomeMemberRoleUpdateReq;
import com.aiot.home.dto.HomeResp;

import java.util.List;

public interface HomeService {
    
    /**
     * 创建家庭
     */
    String createHome(HomeCreateReq req, String userId);

    /**
     * 查询当前用户的家庭列表
     */
    List<HomeResp> listUserHomes(String userId);

    /**
     * 删除家庭 (仅 Owner 可操作)
     */
    void deleteHome(String homeId, String userId);

    /**
     * 查询家庭成员列表
     */
    List<HomeMemberResp> listHomeMembers(String homeId);

    /**
     * 添加家庭成员
     */
    void addHomeMember(String homeId, HomeMemberAddReq req, String operatorUserId);

    /**
     * 修改家庭成员角色（仅支持 2/3）
     */
    void updateHomeMemberRole(String homeId, String targetUserId, HomeMemberRoleUpdateReq req, String operatorUserId);

    /**
     * 移除家庭成员
     */
    void removeHomeMember(String homeId, String targetUserId, String operatorUserId);
}
