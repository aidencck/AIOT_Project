package com.aiot.auth.service;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.dto.EmqxWebhookReq;

/**
 * EMQX设备认证服务，处理MQTT设备连接的身份验证、EMQX事件Webhook的签名校验，以及设备上下线状态处理
 * 核心解决设备接入EMQX集群时的身份合法性校验，以及平台接收EMQX推送的设备事件时的请求合法性校验问题
 */
public interface AuthService {
    /**
     * 认证设备的MQTT连接请求，验证设备是否有权限接入EMQX
     * @param req EMQX转发的设备连接认证请求，包含客户端ID、用户名、密码等连接信息
     * @return 认证是否通过，true为允许连接，false为拒绝连接
     * @throws IllegalArgumentException 当请求参数为空或非法时抛出
     */
    boolean authenticateDevice(EmqxAuthReq req);
    
    /**
     * 校验EMQX Webhook请求的基础签名，验证请求来源是否合法
     * @param action EMQX触发的事件类型（如connect、disconnect等）
     * @param clientId 设备客户端ID
     * @param username 设备连接用户名
     * @param timestamp 请求时间戳，用于防重放攻击
     * @param signatureHeader 请求携带的签名，用于校验请求完整性    
     * @return 签名校验是否通过
     * @throws IllegalArgumentException 当参数为空或时间戳格式非法时抛出
     */
    boolean verifyWebhookSignature(String action, String clientId, String username, Long timestamp, String signatureHeader);
    
    /**
     * 完整校验EMQX Webhook请求，同时校验签名和内部服务令牌，适配内部转发的Webhook场景
     * @param action EMQX触发的事件类型
     * @param clientId 设备客户端ID
     * @param username 设备连接用户名
     * @param timestamp 请求时间戳，用于防重放攻击
     * @param signatureHeader 请求携带的签名
     * @param internalTokenHeader 内部服务令牌，用于校验请求是否来自可信内部服务
     * @return 请求校验是否通过
     * @throws IllegalArgumentException 当参数为空或格式非法时抛出
     */
    boolean verifyWebhook(String action, String clientId, String username, Long timestamp, String signatureHeader, String internalTokenHeader);
    
    /**
     * 处理EMQX推送的设备状态Webhook事件，更新平台内的设备在线/离线状态
     * @param req EMQX推送的Webhook请求，包含事件类型、设备信息、时间等状态数据
     * @throws IllegalArgumentException 当请求参数为空或事件类型非法时抛出
     * @throws RuntimeException 当设备状态更新失败时抛出
     */
    void handleDeviceStatusWebhook(EmqxWebhookReq req);
    
    /**
     * 刷新设备的认证缓存，适用于设备密钥更新后主动刷新缓存场景
     * @param deviceId 设备唯一标识
     * @return 刷新是否成功，true为刷新成功，false为设备不存在或刷新失败
     * @throws IllegalArgumentException 当设备ID为空时抛出
     */
    boolean refreshDeviceAuthCache(String deviceId);
    
    /**
     * 吊销设备的接入权限，强制下线指定设备并拉黑
     * @param clientId 设备客户端ID
     * @param reason 吊销原因，用于日志记录和审计
     * @return 吊销是否成功，true为成功吊销并下线，false为设备不在线或操作失败
     * @throws IllegalArgumentException 当客户端ID为空时抛出
     */
    boolean revokeDeviceAccess(String clientId, String reason);
}
