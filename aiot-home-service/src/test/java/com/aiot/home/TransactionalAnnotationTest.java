package com.aiot.home;

import com.aiot.home.dto.LoginReq;
import com.aiot.home.dto.RegisterReq;
import com.aiot.home.dto.RoomCreateReq;
import com.aiot.home.service.impl.RoomServiceImpl;
import com.aiot.home.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionalAnnotationTest {

    @Test
    void homeWriteMethods_shouldBeTransactional() throws Exception {
        assertWriteTransactional(UserServiceImpl.class.getMethod("login", LoginReq.class));
        assertWriteTransactional(UserServiceImpl.class.getMethod("register", RegisterReq.class));
        assertWriteTransactional(RoomServiceImpl.class.getMethod("createRoom", RoomCreateReq.class, String.class));
        assertWriteTransactional(RoomServiceImpl.class.getMethod("deleteRoom", String.class, String.class, String.class));
    }

    private static void assertWriteTransactional(Method method) {
        Transactional tx = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        assertNotNull(tx, () -> "Missing @Transactional on " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
        assertTrue(tx.rollbackFor().length == 1 && tx.rollbackFor()[0] == Exception.class,
                () -> "Expected rollbackFor=Exception.class on " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
        assertTrue(!tx.readOnly(), () -> "Expected readOnly=false on " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
    }
}
