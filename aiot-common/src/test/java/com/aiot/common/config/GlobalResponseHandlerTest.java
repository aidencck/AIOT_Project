package com.aiot.common.config;

import com.aiot.common.api.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class GlobalResponseHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GlobalResponseHandler handler =
            new GlobalResponseHandler(objectMapper, new ResponseContractResolver());

    @Test
    void shouldBypassWhenMethodHasSkipResponseWrap() throws Exception {
        MethodParameter returnType = method("skipWrapped");
        Object body = "raw";

        Object result = handler.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                converterType(),
                request("/api/v1/custom/callback"),
                response());

        assertSame(body, result);
    }

    @Test
    void shouldBypassWhenProtocolPathMatches() throws Exception {
        MethodParameter returnType = method("normal");
        Object body = "allow";

        Object result = handler.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                converterType(),
                request("/api/v1/emqx/auth"),
                response());

        assertSame(body, result);
    }

    @Test
    void shouldWrapStringAsResultJson() throws Exception {
        MethodParameter returnType = method("normal");

        Object result = handler.beforeBodyWrite(
                "ok",
                returnType,
                MediaType.APPLICATION_JSON,
                converterType(),
                request("/api/v1/home/users"),
                response());

        assertInstanceOf(String.class, result);
        JsonNode root = objectMapper.readTree((String) result);
        assertEquals(200, root.get("code").asInt());
        assertEquals("操作成功", root.get("message").asText());
        assertEquals("ok", root.get("data").asText());
    }

    @Test
    void shouldWrapPojoAsResultObject() throws Exception {
        MethodParameter returnType = method("normal");
        Map<String, Object> body = Map.of("id", "u1");

        Object result = handler.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                converterType(),
                request("/api/v1/home/users"),
                response());

        assertInstanceOf(Result.class, result);
        Result<?> wrapped = (Result<?>) result;
        assertEquals(200, wrapped.getCode());
        assertEquals(body, wrapped.getData());
    }

    @Test
    void shouldKeepResultBodyUnchanged() throws Exception {
        MethodParameter returnType = method("normal");
        Result<String> body = Result.success("already-wrapped");

        Object result = handler.beforeBodyWrite(
                body,
                returnType,
                MediaType.APPLICATION_JSON,
                converterType(),
                request("/api/v1/home/users"),
                response());

        assertSame(body, result);
    }

    @Test
    void shouldKeepBodyUnchangedForEventStream() throws Exception {
        MethodParameter returnType = method("normal");
        String body = "stream-data";

        Object result = handler.beforeBodyWrite(
                body,
                returnType,
                MediaType.TEXT_EVENT_STREAM,
                converterType(),
                request("/api/v1/home/users"),
                response());

        assertSame(body, result);
    }

    @Test
    void shouldSupportNormalReturnType() throws Exception {
        boolean supports = handler.supports(method("normal"), converterType());
        assertTrue(supports);
    }

    private MethodParameter method(String name) throws Exception {
        Method method = DummyController.class.getDeclaredMethod(name);
        return new MethodParameter(method, -1);
    }

    private Class<? extends HttpMessageConverter<?>> converterType() {
        return MappingJackson2HttpMessageConverter.class;
    }

    private ServerHttpRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return new ServletServerHttpRequest(request);
    }

    private ServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }

    static class DummyController {
        public Object normal() {
            return null;
        }

        @SkipResponseWrap
        public Object skipWrapped() {
            return null;
        }
    }
}
