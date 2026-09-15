package com.aiot.shadow;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowApplicationComponentScanTest {

    @Test
    void shouldScanComAiotBasePackage() {
        ComponentScan scan = ShadowApplication.class.getAnnotation(ComponentScan.class);

        assertNotNull(scan);
        assertTrue(Arrays.asList(scan.basePackages()).contains("com.aiot"));
    }
}
