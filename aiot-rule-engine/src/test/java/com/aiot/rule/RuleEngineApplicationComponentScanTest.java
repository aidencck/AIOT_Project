package com.aiot.rule;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineApplicationComponentScanTest {

    @Test
    void shouldScanComAiotBasePackage() {
        ComponentScan scan = RuleEngineApplication.class.getAnnotation(ComponentScan.class);

        assertNotNull(scan);
        assertTrue(Arrays.asList(scan.basePackages()).contains("com.aiot"));
    }
}
