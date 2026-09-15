package com.aiot.device.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiResponseStatusContractTest {

    @Test
    void createEndpointsShouldDocumentCreatedStatus() throws Exception {
        assertResponseCode(DeviceController.class.getMethod("createDevice", com.aiot.device.dto.DeviceReq.class), HttpStatus.CREATED);
        assertResponseCode(ProductController.class.getMethod("createProduct", com.aiot.device.dto.ProductReq.class), HttpStatus.CREATED);
    }

    private void assertResponseCode(Method method, HttpStatus expectedStatus) {
        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);
        assertEquals(expectedStatus, responseStatus.value());

        String documentedStatus = Arrays.stream(method.getAnnotationsByType(ApiResponse.class))
                .map(ApiResponse::responseCode)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 ApiResponse 注解"));
        assertEquals(String.valueOf(expectedStatus.value()), documentedStatus);
    }
}
