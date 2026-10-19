package com.platform.common.rw;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 只读路由注解(参照 ESP esp-rw-java @DataSource 落地): 标注的方法/类走 READ 副本。
 * 方法级注解优先于类级注解; forceWrite=true 时强制走 WRITE 主库(写后强一致读,
 * 避免主从延迟读旧值)。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadOnly {

    /** 强制走 WRITE 主库(写后强一致读)。 */
    boolean forceWrite() default false;
}
