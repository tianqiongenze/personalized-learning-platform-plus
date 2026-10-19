package com.platform.common.rw;

/**
 * 基于 ThreadLocal 的路由上下文(参照 ESP esp-rw-java RoutingHolder 落地)。
 * 默认 WRITE(主库), 保证未标注 @ReadOnly 的方法走主库(安全默认, 写操作零感知)。
 *
 * 虚拟线程语义: 每个虚拟线程是独立 Thread, ThreadLocal 读写作用于该虚拟线程自身;
 * 请求全链路(Tomcat/Jetty accept -> 虚拟线程处理 -> @ReadOnly AOP set/proceed/clear)
 * 在同一虚拟线程内完成, 无跨线程传递, 路由正确。
 * TODO(不强改): 若未来引入异步链(@Async/CompletableFuture), 需显式传递路由上下文;
 * 届时评估迁移 ScopedValue(JDK 25 stable, 不可变+有界共享)。
 */
public final class RoutingHolder {
    private static final ThreadLocal<DataSourceType> CTX = new ThreadLocal<>();

    private RoutingHolder() {}

    /** 当前线程设置路由 Key。 */
    public static void set(DataSourceType type) {
        CTX.set(type);
    }

    /** 读取当前路由 Key, 未设置时返回默认 WRITE。 */
    public static DataSourceType get() {
        DataSourceType t = CTX.get();
        return t == null ? DataSourceType.WRITE : t;
    }

    /** 清除当前线程的路由标记, 恢复默认 WRITE。 */
    public static void clear() {
        CTX.remove();
    }
}
