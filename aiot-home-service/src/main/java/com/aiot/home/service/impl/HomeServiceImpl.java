package com.aiot.home.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.home.dto.HomeCreateReq;
import com.aiot.home.dto.HomeMemberAddReq;
import com.aiot.home.dto.HomeMemberResp;
import com.aiot.home.dto.HomeMemberRoleUpdateReq;
import com.aiot.home.dto.HomeResp;
import com.aiot.home.entity.Home;
import com.aiot.home.entity.HomeMember;
import com.aiot.home.entity.User;
import com.aiot.home.repository.HomeMemberRepository;
import com.aiot.home.repository.HomeRepository;
import com.aiot.home.repository.UserRepository;
import com.aiot.home.service.HomeCacheManager;
import com.aiot.home.service.HomeDeviceCompensationService;
import com.aiot.home.service.HomeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HomeServiceImpl implements HomeService {

    @Autowired
    private HomeRepository homeRepository;

    @Autowired
    private HomeMemberRepository homeMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HomeCacheManager homeCacheManager;

    @Autowired
    private HomeDeviceCompensationService homeDeviceCompensationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createHome(HomeCreateReq req, String userId) {
        // 1. 创建家庭实体
        Home home = new Home();
        home.setName(req.getName());
        home.setLocation(req.getLocation());
        homeRepository.insert(home);

        // 2. 将当前用户设为家庭所有者 (Role: 1-Owner)
        HomeMember member = new HomeMember();
        member.setHomeId(home.getId());
        member.setUserId(userId);
        member.setRole(1); 
        homeMemberRepository.insert(member);

        // 3. 刷新缓存
        homeCacheManager.updateUserRoleCache(home.getId(), userId, 1);

        return home.getId();
    }

    @Override
    public List<HomeResp> listUserHomes(String userId) {
        // 查询当前用户参与的所有家庭记录
        LambdaQueryWrapper<HomeMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(HomeMember::getUserId, userId);
        List<HomeMember> members = homeMemberRepository.selectList(memberWrapper);

        if (members.isEmpty()) {
            return List.of();
        }

        // 获取所有关联的家庭 ID 和角色
        List<String> homeIds = members.stream().map(HomeMember::getHomeId).collect(Collectors.toList());
        Map<String, Integer> homeRoleMap = members.stream()
                .collect(Collectors.toMap(HomeMember::getHomeId, HomeMember::getRole));

        // 批量查询家庭信息
        List<Home> homes = homeRepository.selectBatchIds(homeIds);

        // 组装返回对象
        return homes.stream().map(home -> {
            HomeResp resp = new HomeResp();
            resp.setId(home.getId());
            resp.setName(home.getName());
            resp.setLocation(home.getLocation());
            resp.setRole(homeRoleMap.get(home.getId()));
            return resp;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHome(String homeId, String userId) {
        // 注解已在 Controller 层校验了 Owner 权限，此处无需再硬编码校验
        // 先补偿设备域引用，避免 home 删除后产生悬挂 homeId
        homeDeviceCompensationService.unbindDevicesByHomeId(homeId);

        // 删除家庭本身
        homeRepository.deleteById(homeId);

        // 删除家庭的所有成员关联
        LambdaQueryWrapper<HomeMember> deleteAllMembers = new LambdaQueryWrapper<>();
        deleteAllMembers.eq(HomeMember::getHomeId, homeId);
        homeMemberRepository.delete(deleteAllMembers);

        // 移除缓存 (Cache Aside: 失效模式)
        homeCacheManager.removeHomeMembersCache(homeId);
        // 未来如果有 Room 和 Device，这里也要一并级联删除或解除绑定
    }

    @Override
    public List<HomeMemberResp> listHomeMembers(String homeId) {
        LambdaQueryWrapper<HomeMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HomeMember::getHomeId, homeId).orderByAsc(HomeMember::getRole, HomeMember::getCreateTime);
        List<HomeMember> members = homeMemberRepository.selectList(wrapper);
        if (members.isEmpty()) {
            return List.of();
        }

        List<String> userIds = members.stream().map(HomeMember::getUserId).distinct().collect(Collectors.toList());
        List<User> users = userRepository.selectBatchIds(userIds);
        Map<String, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        return members.stream().map(m -> {
            HomeMemberResp resp = new HomeMemberResp();
            resp.setUserId(m.getUserId());
            resp.setRole(m.getRole());
            User user = userMap.get(m.getUserId());
            if (user != null) {
                resp.setNickname(user.getNickname());
                resp.setPhone(user.getPhone());
            }
            return resp;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addHomeMember(String homeId, HomeMemberAddReq req, String operatorUserId) {
        Home home = homeRepository.selectById(homeId);
        if (home == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "家庭不存在");
        }
        User targetUser = userRepository.selectById(req.getUserId());
        if (targetUser == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "目标用户不存在");
        }

        HomeMember operatorMember = getHomeMember(homeId, operatorUserId);
        if (operatorMember == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户未加入该家庭");
        }
        if (req.getRole() == 2 && !Objects.equals(operatorMember.getRole(), 1)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅 Owner 可添加管理员");
        }

        HomeMember existing = getHomeMember(homeId, req.getUserId());
        if (existing != null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户已是家庭成员");
        }

        HomeMember member = new HomeMember();
        member.setHomeId(homeId);
        member.setUserId(req.getUserId());
        member.setRole(req.getRole());
        homeMemberRepository.insert(member);
        homeCacheManager.updateUserRoleCache(homeId, req.getUserId(), req.getRole());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateHomeMemberRole(String homeId, String targetUserId, HomeMemberRoleUpdateReq req, String operatorUserId) {
        HomeMember operatorMember = getHomeMember(homeId, operatorUserId);
        if (operatorMember == null || !Objects.equals(operatorMember.getRole(), 1)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅 Owner 可修改成员角色");
        }

        HomeMember targetMember = getHomeMember(homeId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "目标成员不存在");
        }
        if (Objects.equals(targetMember.getRole(), 1)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能修改 Owner 角色");
        }

        targetMember.setRole(req.getRole());
        homeMemberRepository.updateById(targetMember);
        homeCacheManager.updateUserRoleCache(homeId, targetUserId, req.getRole());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeHomeMember(String homeId, String targetUserId, String operatorUserId) {
        HomeMember operatorMember = getHomeMember(homeId, operatorUserId);
        if (operatorMember == null || !Objects.equals(operatorMember.getRole(), 1)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅 Owner 可移除成员");
        }
        if (Objects.equals(targetUserId, operatorUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Owner 不能移除自己");
        }

        HomeMember targetMember = getHomeMember(homeId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "目标成员不存在");
        }
        if (Objects.equals(targetMember.getRole(), 1)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能移除 Owner");
        }

        homeMemberRepository.deleteById(targetMember.getId());
        homeCacheManager.removeUserRoleCache(homeId, targetUserId);
    }

    private HomeMember getHomeMember(String homeId, String userId) {
        LambdaQueryWrapper<HomeMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HomeMember::getHomeId, homeId).eq(HomeMember::getUserId, userId);
        return homeMemberRepository.selectOne(wrapper);
    }
}
