package com.aiot.home.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.config.RedisUtils;
import com.aiot.common.exception.BusinessException;
import com.aiot.home.dto.HomeCreateReq;
import com.aiot.home.dto.HomeMemberAddReq;
import com.aiot.home.dto.HomeMemberResp;
import com.aiot.home.dto.HomeMemberRoleUpdateReq;
import com.aiot.home.dto.HomeResp;
import com.aiot.home.dto.HomeUpdateReq;
import com.aiot.home.event.HomeCacheOp;
import com.aiot.home.event.HomeCacheUpdateEvent;
import com.aiot.home.event.HomeDeleteCompensationEvent;
import com.aiot.home.entity.Home;
import com.aiot.home.entity.HomeMember;
import com.aiot.home.entity.User;
import com.aiot.home.repository.HomeMemberRepository;
import com.aiot.home.repository.HomeRepository;
import com.aiot.home.repository.UserRepository;
import com.aiot.home.service.HomeDeleteCompensationTaskService;
import com.aiot.home.service.HomeService;
import com.aiot.home.service.RoomService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private HomeDeleteCompensationTaskService compensationTaskService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private RedisUtils redisUtils;

    private static final long CREATE_HOME_LOCK_TTL_SECONDS = 30L;
    private static final int CREATE_HOME_LOCK_WAIT_ATTEMPTS = 10;
    private static final long CREATE_HOME_LOCK_WAIT_INTERVAL_MS = 100L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createHome(HomeCreateReq req, String userId) {
        String lockKey = redisUtils.buildKey("home", "create", userId + ":" + req.getName());
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtils.setIfAbsentString(lockKey, lockValue, CREATE_HOME_LOCK_TTL_SECONDS, TimeUnit.SECONDS)) {
            // 并发创建同一用户同名家庭：等待持锁请求提交后复用已有家庭，避免重复创建
            String existingHomeId = waitExistingHomeByNameAndOwner(req.getName(), userId);
            if (existingHomeId != null) {
                return existingHomeId;
            }
            throw new BusinessException(ResultCode.FAILED, "创建家庭并发冲突，请稍后重试");
        }

        try {
            releaseLockAfterTransaction(lockKey, lockValue);

            // 幂等：同一用户已存在同名家庭时直接复用，避免重复家庭及设备 homeId 漂移。
            String existingHomeId = findExistingHomeIdByNameAndOwner(req.getName(), userId);
            if (existingHomeId != null) {
                return existingHomeId;
            }

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

            // 3. 事务提交后刷新缓存
            eventPublisher.publishEvent(new HomeCacheUpdateEvent(HomeCacheOp.UPDATE_USER_ROLE, home.getId(), userId, 1));

            return home.getId();
        } finally {
            // 无事务直调场景兜底释放；有事务时由 afterCompletion 释放，避免提交前释放导致并发穿透。
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                redisUtils.releaseIfHeld(lockKey, lockValue);
            }
        }
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
        if (!existsHome(homeId)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "家庭不存在");
        }
        roomService.deleteRoomsByHomeId(homeId);
        homeRepository.deleteById(homeId);
        LambdaQueryWrapper<HomeMember> deleteAllMembers = new LambdaQueryWrapper<>();
        deleteAllMembers.eq(HomeMember::getHomeId, homeId);
        homeMemberRepository.delete(deleteAllMembers);
        eventPublisher.publishEvent(new HomeCacheUpdateEvent(HomeCacheOp.REMOVE_HOME_MEMBERS, homeId, null, null));

        String taskId = compensationTaskService.createPendingTask(homeId);
        eventPublisher.publishEvent(new HomeDeleteCompensationEvent(taskId));
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
        eventPublisher.publishEvent(new HomeCacheUpdateEvent(HomeCacheOp.UPDATE_USER_ROLE, homeId, req.getUserId(), req.getRole()));
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
        eventPublisher.publishEvent(new HomeCacheUpdateEvent(HomeCacheOp.UPDATE_USER_ROLE, homeId, targetUserId, req.getRole()));
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
        eventPublisher.publishEvent(new HomeCacheUpdateEvent(HomeCacheOp.REMOVE_USER_ROLE, homeId, targetUserId, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateHome(String homeId, HomeUpdateReq req, String userId) {
        Home home = homeRepository.selectById(homeId);
        if (home == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "家庭不存在");
        }

        HomeMember member = getHomeMember(homeId, userId);
        if (member == null || member.getRole() == null || member.getRole() > 2) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限修改家庭");
        }

        if (req.getName() != null && !req.getName().isEmpty()) {
            home.setName(req.getName());
        }
        if (req.getLocation() != null && !req.getLocation().isEmpty()) {
            home.setLocation(req.getLocation());
        }
        homeRepository.updateById(home);
    }

    private HomeMember getHomeMember(String homeId, String userId) {
        LambdaQueryWrapper<HomeMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HomeMember::getHomeId, homeId).eq(HomeMember::getUserId, userId);
        return homeMemberRepository.selectOne(wrapper);
    }

    private String waitExistingHomeByNameAndOwner(String name, String userId) {
        for (int i = 0; i < CREATE_HOME_LOCK_WAIT_ATTEMPTS; i++) {
            String existingHomeId = findExistingHomeIdByNameAndOwner(name, userId);
            if (existingHomeId != null) {
                return existingHomeId;
            }
            try {
                Thread.sleep(CREATE_HOME_LOCK_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.FAILED, "创建家庭等待被中断，请重试");
            }
        }
        return null;
    }

    private void releaseLockAfterTransaction(String lockKey, String lockValue) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                redisUtils.releaseIfHeld(lockKey, lockValue);
            }
        });
    }

    private String findExistingHomeIdByNameAndOwner(String name, String userId) {
        LambdaQueryWrapper<HomeMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(HomeMember::getUserId, userId).eq(HomeMember::getRole, 1);
        List<HomeMember> members = homeMemberRepository.selectList(memberWrapper);
        if (members.isEmpty()) {
            return null;
        }
        List<String> homeIds = members.stream().map(HomeMember::getHomeId).collect(Collectors.toList());
        LambdaQueryWrapper<Home> homeWrapper = new LambdaQueryWrapper<>();
        homeWrapper.in(Home::getId, homeIds).eq(Home::getName, name).orderByAsc(Home::getCreateTime);
        List<Home> homes = homeRepository.selectList(homeWrapper);
        return homes.isEmpty() ? null : homes.get(0).getId();
    }

    @Override
    public boolean existsHome(String homeId) {
        return homeRepository.selectById(homeId) != null;
    }
}
