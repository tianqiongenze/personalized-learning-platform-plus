package com.platform.common.rw;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** @ReadOnly 切面单测(参照 ESP esp-rw-java DataSourceAspectTest 风格, Mockito mock ProceedingJoinPoint)。 */
class ReadOnlyAspectTest {

    private final ReadOnlyAspect aspect = new ReadOnlyAspect();

    @AfterEach
    void cleanup() {
        RoutingHolder.clear();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    /** 用注解标注的方法, 供切面反射读取。 */
    @ReadOnly
    void readMethod() {}

    @ReadOnly(forceWrite = true)
    void forceWriteMethod() {}

    @ReadOnly
    void noopRead() {}

    private ProceedingJoinPoint mockPjp(Method method, AtomicReference<DataSourceType> seenDuringProceed) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        org.aspectj.lang.reflect.MethodSignature sig = mock(org.aspectj.lang.reflect.MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.proceed()).thenAnswer(inv -> {
            seenDuringProceed.set(RoutingHolder.get());
            return null;
        });
        return pjp;
    }

    @Test
    void readAnnotationSetsReadDuringProceedAndRestores() throws Throwable {
        AtomicReference<DataSourceType> seen = new AtomicReference<>();
        Method m = ReadOnlyAspectTest.class.getDeclaredMethod("readMethod");
        RoutingHolder.set(DataSourceType.WRITE); // prev=WRITE
        ProceedingJoinPoint pjp = mockPjp(m, seen);
        aspect.around(pjp);
        assertEquals(DataSourceType.READ, seen.get(), "proceed 期间应为 READ");
        assertEquals(DataSourceType.WRITE, RoutingHolder.get(),
                "恢复后 prev=WRITE 应被 clear(默认 WRITE)");
        verify(pjp, times(1)).proceed();
    }

    @Test
    void nestedPrevReadRestoreReadAndNoClear() throws Throwable {
        AtomicReference<DataSourceType> seen = new AtomicReference<>();
        Method m = ReadOnlyAspectTest.class.getDeclaredMethod("readMethod");
        RoutingHolder.set(DataSourceType.READ); // prev=READ(嵌套外层已是 READ)
        ProceedingJoinPoint pjp = mockPjp(m, seen);
        aspect.around(pjp);
        assertEquals(DataSourceType.READ, seen.get());
        assertEquals(DataSourceType.READ, RoutingHolder.get(),
                "恢复后 prev=READ 不被 clear(嵌套恢复)");
        verify(pjp, times(1)).proceed();
    }

    @Test
    void forceWriteKeepsWriteDuringProceed() throws Throwable {
        AtomicReference<DataSourceType> seen = new AtomicReference<>();
        Method m = ReadOnlyAspectTest.class.getDeclaredMethod("forceWriteMethod");
        RoutingHolder.clear();
        ProceedingJoinPoint pjp = mockPjp(m, seen);
        aspect.around(pjp);
        assertEquals(DataSourceType.WRITE, seen.get(), "forceWrite=true 应保持 WRITE(写后强一致读)");
        verify(pjp, times(1)).proceed();
    }

    @Test
    void activeWriteTransactionForcesWriteAndSkipsReadMarker() throws Throwable {
        AtomicReference<DataSourceType> seen = new AtomicReference<>();
        Method m = ReadOnlyAspectTest.class.getDeclaredMethod("readMethod");
        RoutingHolder.clear();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        try {
            ProceedingJoinPoint pjp = mockPjp(m, seen);
            aspect.around(pjp);
            assertEquals(DataSourceType.WRITE, seen.get(),
                    "已有写事务时不能安全切换连接, 强制 WRITE(跳过 READ 标记)");
            verify(pjp, times(1)).proceed();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void readOnlyTransactionStillRoutesRead() throws Throwable {
        AtomicReference<DataSourceType> seen = new AtomicReference<>();
        Method m = ReadOnlyAspectTest.class.getDeclaredMethod("readMethod");
        RoutingHolder.clear();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            ProceedingJoinPoint pjp = mockPjp(m, seen);
            aspect.around(pjp);
            assertEquals(DataSourceType.READ, seen.get(),
                    "只读事务内允许标记 READ");
            verify(pjp, times(1)).proceed();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.clear();
        }
    }
}
