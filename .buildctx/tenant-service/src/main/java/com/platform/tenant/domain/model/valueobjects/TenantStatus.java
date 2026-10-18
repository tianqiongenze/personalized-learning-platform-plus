package com.platform.tenant.domain.model.valueobjects;

/**
 * 租户状态枚举
 * 定义租户生命周期状态及状态流转规则
 */
public enum TenantStatus {
    /**
     * 草稿态 - 租户创建中，未激活
     */
    DRAFT("草稿", false),

    /**
     * 激活态 - 正常使用中
     */
    ACTIVE("激活", true),

    /**
     * 暂停态 - 管理员暂停，不可使用但数据保留
     */
    SUSPENDED("暂停", false),

    /**
     * 过期态 - 订阅过期，进入宽限期
     */
    EXPIRED("过期", false),

    /**
     * 删除态 - 软删除，进入回收站，可恢复
     */
    DELETED("已删除", false),

    /**
     * 锁定态 - 违规锁定，需人工审核
     */
    LOCKED("锁定", false);

    private final String displayName;
    private final boolean active;

    TenantStatus(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }

    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }

    /**
     * 判断是否可以从当前状态流转到目标状态
     */
    public boolean canTransitionTo(TenantStatus target) {
        if (this == target) return true;

        return switch (this) {
            case DRAFT -> target == ACTIVE || target == DELETED;
            case ACTIVE -> target == SUSPENDED || target == EXPIRED || target == LOCKED || target == DELETED;
            case SUSPENDED -> target == ACTIVE || target == DELETED;
            case EXPIRED -> target == ACTIVE || target == DELETED;
            case LOCKED -> target == ACTIVE || target == DELETED;
            case DELETED -> target == ACTIVE; // 仅支持恢复到激活态
        };
    }

    /**
     * 执行状态流转，不合法则抛出异常
     */
    public TenantStatus transitionTo(TenantStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("租户状态不允许从 %s 流转到 %s", this.displayName, target.displayName)
            );
        }
        return target;
    }

    /**
     * 获取所有可用的下一状态
     */
    public TenantStatus[] getAvailableTransitions() {
        return switch (this) {
            case DRAFT -> new TenantStatus[]{ACTIVE, DELETED};
            case ACTIVE -> new TenantStatus[]{SUSPENDED, EXPIRED, LOCKED, DELETED};
            case SUSPENDED -> new TenantStatus[]{ACTIVE, DELETED};
            case EXPIRED -> new TenantStatus[]{ACTIVE, DELETED};
            case LOCKED -> new TenantStatus[]{ACTIVE, DELETED};
            case DELETED -> new TenantStatus[]{ACTIVE};
        };
    }
}