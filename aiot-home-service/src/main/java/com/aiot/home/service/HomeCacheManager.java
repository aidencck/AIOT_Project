package com.aiot.home.service;

import com.aiot.common.config.RedisUtils;
import com.aiot.home.entity.HomeMember;
import com.aiot.home.repository.HomeMemberRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 家庭缓存管理
 * 负责维护基于 Redis Hash 的家庭成员和角色信息
 */
@Service
public class HomeCacheManager {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private HomeMemberRepository homeMemberRepository;

    /**
     * 重新加载某个家庭的成员角色缓存
     */
    public void reloadHomeMembersCache(String homeId) {
        String key = redisUtils.buildKey("home", "members", homeId);
        
        LambdaQueryWrapper<HomeMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HomeMember::getHomeId, homeId);
        List<HomeMember> members = homeMemberRepository.selectList(wrapper);

        // 删除旧缓存
        redisUtils.delete(key);

        if (!members.isEmpty()) {
            Map<String, Integer> memberRoleMap = members.stream()
                    .collect(Collectors.toMap(HomeMember::getUserId, HomeMember::getRole));
            
            // 使用 Hash 结构批量写入 Redis
            redisTemplate.opsForHash().putAll(key, memberRoleMap);
            // 默认设置 7 天过期时间
            redisTemplate.expire(key, 7, java.util.concurrent.TimeUnit.DAYS);
        }
    }

    /**
     * 获取用户在某个家庭中的角色
     * 优先查缓存，缓存未命中则查数据库并重建缓存
     */
    public Integer getUserRole(String homeId, String userId) {
        String key = redisUtils.buildKey("home", "members", homeId);
        Object roleObj = redisTemplate.opsForHash().get(key, userId);

        if (roleObj != null) {
            return (Integer) roleObj;
        }

        // 缓存未命中（可能是刚过期或 Redis 宕机重启），重新加载
        reloadHomeMembersCache(homeId);
        
        roleObj = redisTemplate.opsForHash().get(key, userId);
        return roleObj != null ? (Integer) roleObj : null;
    }

    /**
     * 更新单个用户的角色缓存
     */
    public void updateUserRoleCache(String homeId, String userId, Integer role) {
        String key = redisUtils.buildKey("home", "members", homeId);
        redisTemplate.opsForHash().put(key, userId, role);
    }

    /**
     * 移除单个用户角色缓存
     */
    public void removeUserRoleCache(String homeId, String userId) {
        String key = redisUtils.buildKey("home", "members", homeId);
        redisTemplate.opsForHash().delete(key, userId);
    }

    /**
     * 移除整个家庭的成员缓存（缓存失效模式）
     */
    public void removeHomeMembersCache(String homeId) {
        String key = redisUtils.buildKey("home", "members", homeId);
        redisUtils.delete(key);
    }
}
