package com.aiot.common.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class TracePropagationTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldReturnNullWhenTraceIdAbsent() {
        assertNull(TracePropagation.currentTraceId());
    }

    @Test
    void shouldReadTraceIdFromMdc() {
        MDC.put(TracePropagation.MDC_TRACE_ID_KEY, "trace-001");

        assertEquals("trace-001", TracePropagation.currentTraceId());
    }

    @Test
    void shouldAppendTraceHeaderForReactiveRequests() {
        MDC.put(TracePropagation.MDC_TRACE_ID_KEY, "trace-reactive");
        TracePropagationExchangeFilterFunction filter = new TracePropagationExchangeFilterFunction();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        ClientResponse response = mock(ClientResponse.class);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost/test")).build();

        filter.filter(request, nextRequest -> {
            captured.set(nextRequest);
            return reactor.core.publisher.Mono.just(response);
        }).block();

        assertEquals("trace-reactive",
                captured.get().headers().getFirst(TracePropagation.TRACE_ID_HEADER));
    }

    @Test
    void shouldPreserveExistingReactiveTraceHeader() {
        MDC.put(TracePropagation.MDC_TRACE_ID_KEY, "trace-mdc");
        TracePropagationExchangeFilterFunction filter = new TracePropagationExchangeFilterFunction();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ClientResponse response = mock(ClientResponse.class);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost/test"))
                .header(TracePropagation.TRACE_ID_HEADER, "trace-original")
                .build();

        filter.filter(request, nextRequest -> {
            captured.set(nextRequest);
            return reactor.core.publisher.Mono.just(response);
        }).block();

        assertEquals("trace-original",
                captured.get().headers().getFirst(TracePropagation.TRACE_ID_HEADER));
    }

    @Test
    void shouldAppendTraceHeaderForBlockingRequests() throws IOException {
        MDC.put(TracePropagation.MDC_TRACE_ID_KEY, "trace-blocking");
        TracePropagationRequestInterceptor interceptor = new TracePropagationRequestInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost/test"));

        interceptor.intercept(request, new byte[0], successExecution());

        assertEquals("trace-blocking", request.getHeaders().getFirst(TracePropagation.TRACE_ID_HEADER));
    }

    @Test
    void shouldKeepExistingBlockingTraceHeader() throws IOException {
        MDC.put(TracePropagation.MDC_TRACE_ID_KEY, "trace-mdc");
        TracePropagationRequestInterceptor interceptor = new TracePropagationRequestInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost/test"));
        request.getHeaders().set(TracePropagation.TRACE_ID_HEADER, "trace-original");

        interceptor.intercept(request, new byte[0], successExecution());

        assertEquals("trace-original", request.getHeaders().getFirst(TracePropagation.TRACE_ID_HEADER));
    }

    private ClientHttpRequestExecution successExecution() {
        return (httpRequest, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);
    }
}
