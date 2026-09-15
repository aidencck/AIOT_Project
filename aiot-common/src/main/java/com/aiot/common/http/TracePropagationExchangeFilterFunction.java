package com.aiot.common.http;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * 为响应式 WebClient 跨服务调用透传 X-Trace-Id。
 */
public class TracePropagationExchangeFilterFunction implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String traceId = TracePropagation.currentTraceId();
        if (traceId != null && !request.headers().containsKey(TracePropagation.TRACE_ID_HEADER)) {
            ClientRequest mutated = ClientRequest.from(request)
                    .header(TracePropagation.TRACE_ID_HEADER, traceId)
                    .build();
            return next.exchange(mutated);
        }
        return next.exchange(request);
    }
}
