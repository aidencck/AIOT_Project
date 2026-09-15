package com.aiot.common.http;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 跨服务链路 traceId 透传工具，统一 header 名与 MDC key。
 */
public final class TracePropagation {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    private TracePropagation() {
    }

    /**
     * 从当前线程 MDC 读取 traceId，无则返回 null。
     */
    public static String currentTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID_KEY);
        return StringUtils.hasText(traceId) ? traceId : null;
    }
}
