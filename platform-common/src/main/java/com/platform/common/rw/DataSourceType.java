package com.platform.common.rw;

/**
 * 数据源类型: 读写分离(参照 ESP esp-rw-java 模式落地)。
 * WRITE -> 主库(CRDB 主 svc, NodePort 26257); READ -> 副本(k8s cockroachdb-read svc, NodePort 26267)。
 * CRDB 场景: read-url 可指向 CRDB follower/replica 节点, write 指向主 svc,
 * 由 CRDB 层保证副本最终一致; 写后强一致读走主库(@ReadOnly(forceWrite=true))。
 */
public enum DataSourceType {
    WRITE,
    READ
}
