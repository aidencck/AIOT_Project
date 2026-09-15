package com.aiot.device;

import com.aiot.device.dto.ProductReq;
import com.aiot.device.service.impl.DeviceServiceImpl;
import com.aiot.device.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionalAnnotationTest {

    @Test
    void deviceWriteMethods_shouldBeTransactional() throws Exception {
        assertWriteTransactional(ProductServiceImpl.class.getMethod("createProduct", ProductReq.class));
        assertWriteTransactional(ProductServiceImpl.class.getMethod("updateThingModel", String.class, String.class));
        assertWriteTransactional(DeviceServiceImpl.class.getMethod("updateDeviceStatus", String.class, Integer.class));
        assertWriteTransactional(DeviceServiceImpl.class.getMethod("touchHeartbeat", String.class));
    }

    private static void assertWriteTransactional(Method method) {
        Transactional tx = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        assertNotNull(tx, () -> "Missing @Transactional on " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
        assertTrue(tx.rollbackFor().length == 1 && tx.rollbackFor()[0] == Exception.class,
                () -> "Expected rollbackFor=Exception.class on " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
        assertTrue(!tx.readOnly(), () -> "Expected readOnly=false on " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
    }
}
