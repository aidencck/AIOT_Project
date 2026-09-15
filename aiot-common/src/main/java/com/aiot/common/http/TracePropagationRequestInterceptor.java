package com.aiot.common.http;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 为阻塞式 RestClient 跨服务调用透传 X-Trace-Id。
 */
public class TracePropagationRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String traceId = TracePropagation.currentTraceId();
        if (traceId != null && !request.getHeaders().containsKey(TracePropagation.TRACE_ID_HEADER)) {
            request.getHeaders().set(TracePropagation.TRACE_ID_HEADER, traceId);
        }
        return execution.execute(request, body);
    }
}
