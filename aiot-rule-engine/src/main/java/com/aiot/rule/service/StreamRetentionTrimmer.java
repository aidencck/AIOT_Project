package com.aiot.rule.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 事件流保留治理（XTRIM/MAXLEN）。
 *
 * <p>消费链路侧的兜底裁剪：即使生产侧 {@code RedisUtils.addToStream} 的 trim 因
 * 故障未生效，本组件也按固定周期对 device-event 流执行 XTRIM MAXLEN ~ maxLen，
 * 保证 XLEN 有界，满足验收锚点 XLEN &lt; 10000（默认 maxLen=5000）。</p>
 *
 * <p>与 {@link StreamBacklogMetrics}（观测）、{@link StreamBacklogMigrationGate}
 * （PEL 迁移）职责分离，本组件只负责「保留长度裁剪」这一个控制动作，保持可剥离边界。</p>
 */
@Slf4j
@Component
public class StreamRetentionTrimmer {

    private final StringRedisTemplate stringRedisTemplate;
    private final String streamKey;
    private final boolean enabled;
    private final long maxLen;
    private final Counter trimmedCounter;

    public StreamRetentionTrimmer(
            StringRedisTemplate stringRedisTemplate,
            MeterRegistry meterRegistry,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String streamKey,
            @Value("${aiot.events.stream-retention.enabled:true}") boolean enabled,
            @Value("${aiot.events.stream-maxlen:5000}") long maxLen) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamKey = streamKey;
        this.enabled = enabled;
        this.maxLen = maxLen;
        this.trimmedCounter = Counter.builder("aiot.stream.retention.trimmed.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", streamKey)
                .register(meterRegistry);
    }

    /**
     * 定时执行Redis Stream长度裁剪任务
     * 核心逻辑：
     * 1. 开关/参数校验：如果功能未启用或最大长度配置非法，直接跳过本次执行
     * 2. 调用Redis底层XTRIM命令，使用近似裁剪（~参数）平衡性能与准确性，仅保留最多maxLen条消息
     * 3.  metrics埋点与日志记录：如果成功裁剪了消息，累加监控计数器并打印日志
     * 4. 异常隔离：捕获所有执行异常，避免单个任务失败影响后续周期执行
     * 
     * 底层原理：
     * - Redis Stream的XTRIM MAXLEN ~ 命令：通过近似淘汰旧消息，将流长度控制在目标值附近，相比精确裁剪性能更高
     * - Spring Scheduler定时调度：按配置的固定周期执行，默认60秒一次，确保流长度不会持续超限
     * 
     * 第一性原理：
     * - 有界性原则：任何队列/流必须保证长度可控，避免无限制增长耗尽内存
     * - 兜底保障：即使生产侧的裁剪逻辑失效，本定时任务作为最后一道防线，始终维持流长度在安全阈值内
     * - 故障容错：任务自身的异常不会扩散，保证治理能力的长期可用性
     * 
     * 
     */
    /**
     * @Scheduled注解的执行前提：Spring需通过@EnableScheduling开启调度功能，通常在启动类添加该注解
     * 执行流程：
     * 1. Spring容器启动时，调度线程池（默认ThreadPoolTaskScheduler）会初始化所有@Scheduled任务
     * 2. 本任务使用fixedDelay模式：本次任务执行完成后，等待配置的毫秒数再执行下一次，默认间隔60秒
     * 3. ${...}占位符会在项目启动时被Spring的PlaceholderConfigurer解析，读取配置文件中的间隔值，不存在则使用默认60000ms
     * 4. 调度线程池会持续按规则执行trimStream()方法，直到应用停止
     */
    /**
     * @Scheduled完整参数说明：
     * 1. cron：Cron表达式，指定任务在特定时间执行，支持配置文件占位符如"${schedule.cron:0 0 * * * ?}"
     * 2. zone：指定cron表达式解析的时区，默认是服务器默认时区
     * 3. fixedDelay：固定间隔执行，单位毫秒，上次执行结束后等待指定时间再执行下一次
     * 4. fixedDelayString：与fixedDelay功能一致，支持配置文件占位符，如"${schedule.fixed-delay:60000}"
     * 5. fixedRate：固定频率执行，单位毫秒，不管上次执行是否完成，每次启动时间按固定间隔推移
     * 6. fixedRateString：与fixedRate功能一致，支持配置文件占位符
     * 7. initialDelay：首次执行前的延迟时间，单位毫秒，需配合fixedDelay/fixedRate使用
     * 8. initialDelayString：与initialDelay功能一致，支持配置文件占位符
     * 
     * 使用约束：不能同时使用cron + fixedDelay系列参数，同一@Scheduled仅能选择一种调度模式
     */
    /**
     * 定时任务调度注解全生命周期与时空逻辑说明：
     * 1. 生命周期阶段：
     *    - 启动解析：Spring容器启动后，处理@EnableScheduling时会扫描所有@Scheduled方法，
     *      解析本注解的fixedDelayString占位符，注入配置的间隔值（默认60000ms），注册到默认调度线程池ThreadPoolTaskScheduler
     *    - 运行调度：采用fixedDelay模式，本次trimStream()执行完成后，等待配置的毫秒数再调度下一次执行，
     *      调度状态由Spring的ScheduledTask实例维护，存储在内存的任务列表中
     *    - 停止销毁：应用关闭时，调度线程池会优雅停止所有待执行任务，正在执行的任务会收到中断通知，内存中的任务状态随JVM进程退出而销毁
     * 2. 时空逻辑：
     *    - 时间维度：仅在当前应用实例的进程生命周期内按间隔执行，分布式部署下每个实例都会独立调度执行，无全局时间同步
     *    - 空间维度：任务仅绑定当前Spring应用上下文，无法跨进程/跨节点迁移，每个节点的调度是完全独立的
     * 3. 数据持久化：
     *    - 调度状态无持久化存储，仅保存在进程内存中，应用重启后会重新初始化调度任务，不会恢复未执行的历史调度计划
     *    - 每次裁剪操作产生的trimmed统计数据会通过Micrometer上报到监控系统，实现指标数据的持久化留存
     */
    @Scheduled(fixedDelayString = "${aiot.events.stream-retention.fixed-delay-ms:60000}")
    public void trimStream() {
        if (!enabled || maxLen <= 0) {
            return;
        }
        try {
            // 调用Redis Stream trim方法，第三个参数true表示启用近似裁剪，优先保证执行效率
            Long trimmed = stringRedisTemplate.opsForStream().trim(streamKey, maxLen, true);
            if (trimmed != null && trimmed > 0) {
                trimmedCounter.increment(trimmed);
                log.info("Stream retention trimmed, stream={}, maxLen={}, trimmed={}",
                        streamKey, maxLen, trimmed);
            }
        } catch (Exception ex) {
            log.warn("Failed to trim stream retention, stream={}, maxLen={}", streamKey, maxLen, ex);
        }
    }
}
