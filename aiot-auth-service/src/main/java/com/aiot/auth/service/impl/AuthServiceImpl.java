package com.aiot.auth.service.impl;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.dto.EmqxWebhookReq;
import com.aiot.auth.entity.DeviceCredential;
import com.aiot.auth.repository.DeviceCredentialRepository;
import com.aiot.auth.service.AuthMetrics;
import com.aiot.auth.service.AuthService;
import com.aiot.auth.utils.SignUtils;
import com.aiot.common.config.RedisUtils;
import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.common.security.InternalTokenUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 完整业务时序图（Mermaid格式，可复制到Mermaid渲染工具中查看）
 * ```mermaid
 * sequenceDiagram
 *     participant Device as 物联网设备
 *     participant EMQX as EMQX消息服务器
 *     participant AuthSvc as 认证服务(AuthServiceImpl)
 *     participant MySQL as 设备凭证数据库
 *     participant Redis as Redis缓存
 *     participant Stream as 设备事件流
 *     participant DLQ as 死信队列
 * 
 *     %% 1. 设备连接认证流程
 *     Device->>EMQX: 发起MQTT连接(携带clientid、username、password)
 *     EMQX->>AuthSvc: 调用认证接口(EmqxAuthReq)
 *     AuthSvc->>AuthSvc: 记录认证尝试指标(authMetrics.recordAuthAttempt())
 *     AuthSvc->>MySQL: 查询设备凭证(selectByIdentity(deviceIdentity))
 *     MySQL-->>AuthSvc: 返回DeviceCredential设备凭证
 *     alt 凭证不存在
 *         AuthSvc->>AuthSvc: 记录拒绝指标(credential_not_found)
 *         AuthSvc-->>EMQX: 返回认证失败(false)
 *         EMQX-->>Device: 拒绝连接
 *     else 凭证存在
 *         AuthSvc->>AuthSvc: 计算预期HMAC-SHA256签名
 *         alt 签名匹配
 *             AuthSvc-->>EMQX: 返回认证成功(true)
 *             EMQX->>Device: 建立MQTT连接
 *         else 签名不匹配
 *             AuthSvc->>AuthSvc: 记录拒绝指标(signature_mismatch)
 *             AuthSvc-->>EMQX: 返回认证失败(false)
 *             EMQX-->>Device: 拒绝连接
 *         end
 *     end
 * 
 *     %% 2. 设备状态Webhook处理流程(连接/断开场景)
 *     EMQX->>AuthSvc: 推送设备状态Webhook(EmqxWebhookReq: client.connected/client.disconnected)
 *     AuthSvc->>AuthSvc: 优先校验内部令牌(internalTokenHeader)
 *     alt 内部令牌校验失败
 *         AuthSvc->>AuthSvc: 执行Webhook签名校验流程(verifyWebhookSignature)
 *         alt 签名校验不通过(密钥缺失/签名无效/时间戳过期/重放请求)
 *             AuthSvc-->>EMQX: 返回Webhook校验失败
 *             return
 *         end
 *     end
 *     AuthSvc->>MySQL: 查询设备凭证(selectByIdentity(req.getUsername()))
 *     alt 凭证不存在
 *         AuthSvc-->>EMQX: 忽略Webhook请求
 *         return
 *     end
 *     AuthSvc->>AuthSvc: 解析全局设备ID(resolveGlobalDeviceId)
 *     alt 设备上线事件(client.connected)
 *         AuthSvc->>Redis: 设置在线状态(redisKey=online, TTL=120s)
 *     else 设备下线事件(client.disconnected)
 *         AuthSvc->>Redis: 删除在线状态(delete redisKey)
 *     end
 * 
 *     %% 3. 设备事件发布重试流程
 *     AuthSvc->>AuthSvc: 构造DeviceEvent事件对象并序列化
 *     loop 最多发布重试publishMaxRetries+1次
 *         AuthSvc->>Redis: 写入设备事件流(redisUtils.addToStream)
 *         alt 写入成功
 *             Redis-->>AuthSvc: 返回写入成功
 *             AuthSvc->>Stream: 事件进入主事件流
 *             break 结束发布流程
 *         else 写入失败
 *             alt 仍有剩余重试次数
 *                 AuthSvc->>AuthSvc: 记录重试日志，等待下一次重试
 *             else 已达最大重试次数
 *                 AuthSvc->>AuthSvc: 构造死信事件(添加失败原因、时间戳)
 *                 AuthSvc->>Redis: 写入死信队列(redisUtils.addToStream)
 *                 alt 死信写入成功
 *                     Redis-->>AuthSvc: 返回写入成功
 *                     AuthSvc->>DLQ: 事件进入死信队列
 *                 else 死信写入失败
 *                     AuthSvc->>AuthSvc: 记录严重错误日志
 *                 end
 *             end
 *         end
 *     end
 * ```
 * 时序图核心流程说明：
 * 1. 设备连接认证：设备发起MQTT连接后，EMQX调用认证服务完成身份校验，依赖数据库查询和签名验证
 * 2. Webhook安全校验：EMQX推送状态事件前，认证服务通过内部令牌/签名校验防伪造，Redis防重放攻击
 * 3. 状态同步与事件发布：设备状态同步到Redis缓存，事件通过Redis Stream发布，支持重试和死信兜底
 * 4. 可观测性：全流程埋点指标记录，关键节点日志输出，支持问题排查
 */

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    // 固定配置常量
    private static final String WEBHOOK_REPLAY_KEY_PREFIX = "aiot:auth:webhook:replay:";
    private static final long ONLINE_STATUS_TTL_SECONDS = 120L;
    private static final int SHA256_TRUNCATE_LENGTH = 32;

    // 设备凭证缓存常量（读穿缓存：identity 索引 + globalDeviceId 主缓存）
    private static final String CREDENTIAL_CACHE_ENTITY = "credential";
    private static final String CREDENTIAL_INDEX_ENTITY = "credential-index";
    private static final long CREDENTIAL_CACHE_TTL_SECONDS = 300L;
    private static final long CREDENTIAL_NEGATIVE_TTL_SECONDS = 60L;
    private static final String NEGATIVE_CREDENTIAL_SENTINEL = "";

    // 依赖注入
    @Autowired
    private DeviceCredentialRepository credentialRepository;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private AuthMetrics authMetrics;
    @Autowired
    private ObjectMapper objectMapper;

    // 配置参数绑定：从Spring环境中读取配置文件的属性值，注入到类成员变量中
    // @Value注解核心逻辑与底层原理：
    // 1. 作用：Spring提供的属性注入注解，用于从Environment（环境配置源，包含application.yml/properties、系统环境变量、命令行参数等）
    // 中读取指定key的值，赋值给类的成员变量或方法参数
    // 2. 语法说明："${配置key:默认值}" 是Spring的属性占位符语法，冒号前是要读取的配置项key，冒号后是该配置缺失时使用的默认值
    // 3. 底层流程：
    //    - Spring容器启动时，通过AutowiredAnnotationBeanPostProcessor处理器扫描所有类的@Value注解
    //    - 解析占位符中的配置key，调用PropertyResolver接口从Environment中查询对应的值
    //    - 如果配置key不存在则使用冒号后的默认值，完成类型转换后注入到成员变量
    // 4. 第一性原理：通过统一的配置源管理，将硬编码的配置项外部化，实现代码与配置的解耦，支持不同环境（开发/测试/生产）使用不同配置，无需修改业务代码
    @Value("${aiot.emqx.webhook.secret:}")
    private String webhookSecret;
    @Value("${aiot.emqx.webhook.max-skew-seconds:300}")
    private long webhookMaxSkewSeconds;
    @Value("${aiot.internal.token:}")
    private String internalToken;
    @Value("${aiot.events.device-status-stream:aiot:stream:device-event}")
    private String deviceStatusStream;
    @Value("${aiot.events.device-status-dlq-stream:aiot:stream:device-event:dlq}")
    private String deviceStatusStreamDlq;
    @Value("${aiot.events.publish-max-retries:2}")
    private int publishMaxRetries;

    /**
     * 设备连接认证流程
     * 1. 解析请求参数获取设备身份标识与密码
     * 2. 记录认证尝试指标
     * 3. 从数据库查询设备凭证（兼容多类型身份标识）
     * 4. 凭证不存在则拒绝认证并记录指标
     * 5. 计算预期HMAC-SHA256签名，与请求密码比对
     * 6. 签名匹配则返回认证成功，否则拒绝并记录指标
     */
    @Override
    public boolean authenticateDevice(EmqxAuthReq req) {
        String deviceIdentity = req.getUsername();
        String password = req.getPassword();
        
        log.info("Authenticating device identity: {}", deviceIdentity);
        authMetrics.recordAuthAttempt();

        DeviceCredential credential = resolveCredentialByIdentity(deviceIdentity);
        if (credential == null) {
            log.warn("Device [{}] credential not found", deviceIdentity);
            authMetrics.recordAuthDeny("credential_not_found");
            return false;
        }

        String globalDeviceId = resolveGlobalDeviceId(credential);
        if (isDeviceRevoked(globalDeviceId)) {
            log.warn("Device [{}] access revoked, deny", deviceIdentity);
            authMetrics.recordAuthDeny("revoked");
            return false;
        }

        String expectedPassword = SignUtils.signWithHmacSha256(req.getClientid(), credential.getDeviceSecret());
        if (InternalTokenUtils.matches(expectedPassword, password)) {
            log.info("Device [{}] authenticated successfully, resolvedGlobalDeviceId={}, authIdentity={}, deviceSn={}",
                    deviceIdentity,
                    resolveGlobalDeviceId(credential),
                    credential.getAuthIdentity(),
                    credential.getDeviceSn());
            return true;
        } else {
            log.warn("Device [{}] signature mismatch", deviceIdentity);
            authMetrics.recordAuthDeny("signature_mismatch");
            return false;
        }
    }

    /**
     * Webhook签名校验流程
     * 1. 检查webhook密钥是否配置，未配置则拒绝
     * 2. 检查请求签名与时间戳是否存在，缺失则拒绝
     * 3. 校验时间戳偏差，超出最大允许范围则拒绝
     * 4. 构造签名原payload，计算预期HMAC-SHA256签名
     * 5. 用恒定时间比较法比对签名，不一致则拒绝
     * 6. 生成请求唯一指纹，写入Redis防重放
     * 7. 检测到重放请求则拒绝，否则通过校验
     */
    @Override
    public boolean verifyWebhookSignature(String action, String clientId, String username, Long timestamp, String signatureHeader) {
        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("Webhook secret not configured, deny webhook by default");
            authMetrics.recordWebhookReject("secret_missing");
            return false;
        }
        if (!StringUtils.hasText(signatureHeader) || timestamp == null) {
            authMetrics.recordWebhookReject("signature_missing");
            return false;
        }

        long nowSeconds = System.currentTimeMillis() / 1000;
        if (Math.abs(nowSeconds - timestamp) > webhookMaxSkewSeconds) {
            log.warn("Webhook timestamp out of range, ts={}, now={}", timestamp, nowSeconds);
            authMetrics.recordWebhookReject("timestamp_skew");
            return false;
        }

        String payload = String.format("%s.%s.%s.%d",
                action == null ? "" : action,
                clientId == null ? "" : clientId,
                username == null ? "" : username,
                timestamp);
        String expected = SignUtils.signWithHmacSha256(payload, webhookSecret);
        boolean signatureValid = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
        );
        if (!signatureValid) {
            authMetrics.recordWebhookReject("signature_invalid");
            return false;
        }

        String replayFingerprint = sha256Truncate(signatureHeader);
        String replayKey = WEBHOOK_REPLAY_KEY_PREFIX + replayFingerprint;
        boolean firstSeen = redisUtils.setIfAbsent(
                replayKey,
                "1",
                webhookMaxSkewSeconds,
                TimeUnit.SECONDS
        );
        if (!firstSeen) {
            log.warn("Detected replay webhook, clientId={}, username={}, timestamp={}",
                    clientId, username, timestamp);
            authMetrics.recordWebhookReject("replay");
            return false;
        }
        return true;
    }

    /**
     * Webhook综合校验入口
     * 优先校验内部请求令牌，令牌匹配则直接通过；否则走签名校验流程
     */
    @Override
    public boolean verifyWebhook(String action, String clientId, String username, Long timestamp, String signatureHeader, String internalTokenHeader) {
        if (StringUtils.hasText(internalToken) && InternalTokenUtils.matches(internalToken, internalTokenHeader)) {
            log.info("Webhook verified via internal token, clientId={}, username={}, action={}", clientId, username, action);
            return true;
        }
        return verifyWebhookSignature(action, clientId, username, timestamp, signatureHeader);
    }

    /**
     * SHA256哈希并截断处理
     * 对输入字符串计算SHA256哈希，转为十六进制字符串后截取前32位作为指纹
     */
    private String sha256Truncate(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            String full = hex.toString();
            return full.length() > SHA256_TRUNCATE_LENGTH ? full.substring(0, SHA256_TRUNCATE_LENGTH) : full;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 设备状态webhook处理流程
     * 1. 根据请求用户名查询设备凭证，凭证不存在则忽略请求
     * 2. 解析全局设备ID，构造Redis状态键
     * 3. 连接事件：设置Redis在线状态（120秒TTL），发布上线事件
     * 4. 断连事件：删除Redis在线状态，发布下线事件
     */
    @Override
    public void handleDeviceStatusWebhook(EmqxWebhookReq req) {
        DeviceCredential credential = resolveCredentialByIdentity(req.getUsername());
        if (credential == null) {
            log.warn("Webhook ignored because device identity cannot be resolved, username={}", req.getUsername());
            return;
        }
        String deviceId = resolveGlobalDeviceId(credential);
        String redisKey = redisUtils.buildKey("device", "status", deviceId);
        
        if ("client.connected".equals(req.getAction())) {
            log.debug("Webhook: Device [{}] is ONLINE", deviceId);
            redisUtils.set(redisKey, "online", ONLINE_STATUS_TTL_SECONDS, TimeUnit.SECONDS);
            publishDeviceEvent(deviceId, DeviceEventType.DEVICE_ONLINE, req.getAction(), req.getTimestamp(), credential);
        } else if ("client.disconnected".equals(req.getAction())) {
            log.debug("Webhook: Device [{}] is OFFLINE", deviceId);
            redisUtils.delete(redisKey);
            publishDeviceEvent(deviceId, DeviceEventType.DEVICE_OFFLINE, req.getAction(), req.getTimestamp(), credential);
        }
    }

    /**
     * 设备事件构造与发布流程
     * 1. 转换时间戳格式，生成唯一事件ID
     * 2. 封装设备核心属性到事件详情
     * 3. 构造标准DeviceEvent对象，序列化为JSON
     * 4. 封装流字段，调用带重试的发布方法
     * 5. 序列化失败则记录日志并终止
     */
    private void publishDeviceEvent(String deviceId,
                                    DeviceEventType eventType,
                                    String action,
                                    Long timestamp,
                                    DeviceCredential credential) {
        long eventTimestamp = timestamp == null ? System.currentTimeMillis() : timestamp * 1000;
        String eventId = String.format("%s:%s:%d", deviceId, action, eventTimestamp);
        Map<String, Object> eventDetail = new HashMap<>();
        eventDetail.put("legacyDeviceId", credential == null ? null : credential.getDeviceId());
        eventDetail.put("globalDeviceId", credential == null ? null : resolveGlobalDeviceId(credential));
        eventDetail.put("authIdentity", credential == null ? null : credential.getAuthIdentity());
        eventDetail.put("deviceSn", credential == null ? null : credential.getDeviceSn());
        DeviceEvent event = DeviceEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .deviceId(deviceId)
                .timestamp(eventTimestamp)
                .source("aiot-auth-service")
                .traceId(MDC.get("traceId"))
                .payload(eventDetail)
                .build();
        try {
            String payload = objectMapper.writeValueAsString(event);
            Map<String, String> fields = new HashMap<>();
            fields.put("eventId", eventId);
            fields.put("eventType", eventType.name());
            fields.put("deviceId", deviceId);
            fields.put("payload", payload);
            publishWithRetry(fields, eventType, deviceId);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize device event, eventType={}, deviceId={}", eventType, deviceId, e);
        }
    }

    /**
     * 事件发布重试流程
     * 1. 计算最大尝试次数（重试次数+1）
     * 2. 循环尝试写入Redis流，成功则返回
     * 3. 写入失败且非最后一次尝试，记录日志后重试
     * 4. 所有尝试失败则写入死信队列
     */
    private void publishWithRetry(Map<String, String> fields, DeviceEventType eventType, String deviceId) {
        int maxAttempts = Math.max(1, publishMaxRetries + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                redisUtils.addToStream(deviceStatusStream, fields);
                log.debug("Published device event to stream={}, eventType={}, deviceId={}",
                        deviceStatusStream, eventType, deviceId);
                return;
            } catch (Exception ex) {
                boolean lastAttempt = attempt == maxAttempts;
                if (lastAttempt) {
                    publishToDlq(fields, eventType, deviceId, ex);
                    return;
                }
                log.warn("Publish device event failed, retrying. attempt={}/{}, stream={}, eventType={}, deviceId={}",
                        attempt, maxAttempts, deviceStatusStream, eventType, deviceId, ex);
            }
        }
    }

    /**
     * 死信队列写入流程
     * 1. 复制原事件字段，添加失败原因与时间戳
     * 2. 尝试写入死信流，成功则记录警告日志
     * 3. 死信流写入也失败，记录错误日志
     */
    private void publishToDlq(Map<String, String> originalFields, DeviceEventType eventType, String deviceId, Exception ex) {
        Map<String, String> dlqFields = new HashMap<>(originalFields);
        dlqFields.put("reason", ex.getClass().getSimpleName());
        dlqFields.put("failedAt", String.valueOf(System.currentTimeMillis()));
        try {
            redisUtils.addToStream(deviceStatusStreamDlq, dlqFields);
            log.warn("Published failed device event to DLQ stream={}, eventType={}, deviceId={}",
                    deviceStatusStreamDlq, eventType, deviceId, ex);
        } catch (Exception dlqEx) {
            log.error("Failed to publish device event to stream and DLQ, eventType={}, deviceId={}, stream={}, dlqStream={}",
                    eventType, deviceId, deviceStatusStream, deviceStatusStreamDlq, dlqEx);
        }
    }

    /**
     * 设备认证缓存刷新流程
     * 1. 校验设备ID非空
     * 2. 查询设备凭证，不存在则返回false
     * 3. 删除设备认证缓存，返回true
     */
    @Override
    public boolean refreshDeviceAuthCache(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            throw new IllegalArgumentException("deviceId must not be empty");
        }
        DeviceCredential credential = credentialRepository.selectByIdentity(deviceId);
        if (credential == null) {
            log.warn("Refresh device auth cache failed, device not found: {}", deviceId);
            return false;
        }
        String authCacheKey = redisUtils.buildKey("auth", "credential", resolveGlobalDeviceId(credential));
        redisUtils.delete(authCacheKey);
        log.info("Refreshed device auth cache, deviceId={}, authCacheKey={}", deviceId, authCacheKey);
        return true;
    }

    /**
     * 设备接入权限吊销流程
     * 1. 校验客户端ID非空
     * 2. 查询设备凭证，不存在则返回false
     * 3. 删除设备在线状态强制下线，写入吊销标记拉黑
     * 4. 返回true
     */
    @Override
    public boolean revokeDeviceAccess(String clientId, String reason) {
        if (!StringUtils.hasText(clientId)) {
            throw new IllegalArgumentException("clientId must not be empty");
        }
        DeviceCredential credential = credentialRepository.selectByIdentity(clientId);
        if (credential == null) {
            log.warn("Revoke device access failed, device not found: {}", clientId);
            return false;
        }
        String deviceId = resolveGlobalDeviceId(credential);
        String statusKey = redisUtils.buildKey("device", "status", deviceId);
        String revokedKey = redisUtils.buildKey("auth", "revoked", deviceId);
        redisUtils.delete(statusKey);
        redisUtils.set(revokedKey, StringUtils.hasText(reason) ? reason : "revoked");
        log.info("Revoked device access, clientId={}, deviceId={}, reason={}", clientId, deviceId, reason);
        return true;
    }

    /**
     * 设备凭证读穿缓存查询
     * 1. 先读身份索引（identity -> globalDeviceId），命中负缓存哨兵则直接返回 null
     * 2. 索引命中则读主缓存（aiot:auth:credential:{globalDeviceId}），命中直接返回
     * 3. 任意未命中则回源数据库，并回填索引与主缓存
     * 4. 凭证不存在时写入短 TTL 负缓存，避免未知身份反复击穿数据库
     */
    private DeviceCredential resolveCredentialByIdentity(String identity) {
        if (!StringUtils.hasText(identity)) {
            return null;
        }
        String indexKey = redisUtils.buildKey("auth", CREDENTIAL_INDEX_ENTITY, identity);
        Object indexed = redisUtils.get(indexKey);
        if (indexed instanceof String indexedGlobalDeviceId) {
            if (!StringUtils.hasText(indexedGlobalDeviceId)) {
                return null;
            }
            DeviceCredential cached = readCredentialFromCache(indexedGlobalDeviceId);
            if (cached != null) {
                return cached;
            }
        }

        DeviceCredential credential = credentialRepository.selectByIdentity(identity);
        if (credential == null) {
            redisUtils.set(indexKey, NEGATIVE_CREDENTIAL_SENTINEL, CREDENTIAL_NEGATIVE_TTL_SECONDS, TimeUnit.SECONDS);
            return null;
        }

        String globalDeviceId = resolveGlobalDeviceId(credential);
        writeCredentialToCache(globalDeviceId, credential);
        redisUtils.set(indexKey, globalDeviceId, CREDENTIAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        return credential;
    }

    /**
     * 从主缓存读取设备凭证，命中返回凭证对象，未命中返回 null
     */
    private DeviceCredential readCredentialFromCache(String globalDeviceId) {
        if (!StringUtils.hasText(globalDeviceId)) {
            return null;
        }
        Object cached = redisUtils.get(redisUtils.buildKey("auth", CREDENTIAL_CACHE_ENTITY, globalDeviceId));
        return cached instanceof DeviceCredential credential ? credential : null;
    }

    /**
     * 将设备凭证写入主缓存（aiot:auth:credential:{globalDeviceId}）
     */
    private void writeCredentialToCache(String globalDeviceId, DeviceCredential credential) {
        if (!StringUtils.hasText(globalDeviceId) || credential == null) {
            return;
        }
        redisUtils.set(redisUtils.buildKey("auth", CREDENTIAL_CACHE_ENTITY, globalDeviceId),
                credential, CREDENTIAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 全局设备ID解析
     * 优先返回全局设备ID，不存在则返回原始设备ID
     */
    private String resolveGlobalDeviceId(DeviceCredential credential) {
        if (credential == null) {
            return null;
        }
        return StringUtils.hasText(credential.getGlobalDeviceId()) ? credential.getGlobalDeviceId() : credential.getDeviceId();
    }

    /**
     * 判断设备是否已被吊销（拉黑）。
     * 黑名单 key: aiot:auth:revoked:{globalDeviceId}
     */
    private boolean isDeviceRevoked(String globalDeviceId) {
        if (!StringUtils.hasText(globalDeviceId)) {
            return false;
        }
        return redisUtils.get(redisUtils.buildKey("auth", "revoked", globalDeviceId)) != null;
    }
}
