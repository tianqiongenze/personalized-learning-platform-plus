package com.platform.common.rw;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

/**
 * @ReadOnly 注解 AOP 切面(参照 ESP esp-rw-java DataSourceAspect 落地):
 * 方法入口设置路由 Key 为 READ, 出口恢复/清理, 支持嵌套调用(保存前一个路由 Key,
 * prev=WRITE 时 clear 防止 ThreadLocal 泄漏)。
 *
 * 事务安全语义:
 * - 同一 Spring 事务内不能安全切换连接: 事务绑定的是首个 getConnection 的连接,
 *   后续路由标记不再生效。因此当方法处于"已有写事务"(@Transactional 且当前线程
 *   actualTransactionActive 且非 READ_ONLY)时, 切面强制保持 WRITE 并跳过 READ 标记 ——
 *   即本切面只对无事务方法生效; 类级注解同样经此判断。
 * - 已有只读事务或无事务时正常标记 READ。
 * - 依赖 org.springframework.transaction API; 宿主服务未引 spring-tx 时
 *   (AbstractRoutingDataSource 需 spring-jdbc, 常规宿主必带 spring-tx), 由条件装配兜底。
 *
 * 路由决策优先级: 写事务内强制 WRITE > forceWrite=true > 方法级 @ReadOnly > 类级 @ReadOnly > 默认 WRITE。
 */
@Aspect
public class ReadOnlyAspect {

    @Around("@annotation(com.platform.common.rw.ReadOnly) || @within(com.platform.common.rw.ReadOnly)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();

        // 写事务内不能安全切换连接 -> 强制 WRITE(见类注释"事务安全语义")
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return pjp.proceed();
        }

        boolean isRead = resolveRead(method);
        if (!isRead) {
            return pjp.proceed(); // 非 READ(如类级注解被方法覆盖的场景不存在, 此处兜底 WRITE)
        }

        DataSourceType prev = RoutingHolder.get();
        RoutingHolder.set(DataSourceType.READ);
        try {
            return pjp.proceed();
        } finally {
            RoutingHolder.set(prev);
            if (prev == DataSourceType.WRITE) {
                RoutingHolder.clear();
            }
        }
    }

    /** 解析方法是否应走 READ: forceWrite=false 才是 READ; 方法级注解优先, 其次类级注解。 */
    private boolean resolveRead(Method method) {
        ReadOnly m = method.getAnnotation(ReadOnly.class);
        ReadOnly c = method.getDeclaringClass().getAnnotation(ReadOnly.class);
        if (m != null) {
            return !m.forceWrite();
        }
        if (c != null) {
            return !c.forceWrite();
        }
        // @within 组合点命中的是目标类注解(代理类上 method.getAnnotation 可能为空)
        return c != null || false;
    }
}
