package com.aiot.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewayApplicationComponentScanTest {

    @Test
    void shouldUseDefaultSpringBootComponentScan() {
        ComponentScan scan = GatewayApplication.class.getAnnotation(ComponentScan.class);

        assertNull(scan);
        assertNotNull(GatewayApplication.class.getAnnotation(SpringBootApplication.class));
    }
}
