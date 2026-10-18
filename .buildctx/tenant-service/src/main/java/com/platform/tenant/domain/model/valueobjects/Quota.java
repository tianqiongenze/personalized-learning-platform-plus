package com.platform.tenant.domain.model.valueobjects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/**
 * 配额值对象
 * 封装租户资源配额的业务规则
 */
public final class Quota {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final int maxUsers;
    private final int maxCourses;
    private final int maxStorageGB;
    private final int maxApiCallsPerMin;
    private final int maxConcurrentSessions;
    private final int maxKnowledgePoints;
    private final int maxResourcesPerCourse;

    private Quota(int maxUsers, int maxCourses, int maxStorageGB,
                  int maxApiCallsPerMin, int maxConcurrentSessions) {
        this(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions, 2000, 100);
    }

    private Quota(int maxUsers, int maxCourses, int maxStorageGB,
                  int maxApiCallsPerMin, int maxConcurrentSessions, int maxKnowledgePoints, int maxResourcesPerCourse) {
        validate(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions);
        this.maxUsers = maxUsers;
        this.maxCourses = maxCourses;
        this.maxStorageGB = maxStorageGB;
        this.maxApiCallsPerMin = maxApiCallsPerMin;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.maxKnowledgePoints = Math.max(0, maxKnowledgePoints);
        this.maxResourcesPerCourse = Math.max(0, maxResourcesPerCourse);
    }

    private void validate(int maxUsers, int maxCourses, int maxStorageGB,
                          int maxApiCallsPerMin, int maxConcurrentSessions) {
        if (maxUsers <= 0) throw new IllegalArgumentException("最大用户数必须大于0");
        if (maxCourses <= 0) throw new IllegalArgumentException("最大课程数必须大于0");
        if (maxStorageGB <= 0) throw new IllegalArgumentException("最大存储必须大于0");
        if (maxApiCallsPerMin <= 0) throw new IllegalArgumentException("最大API调用/分钟必须大于0");
        if (maxConcurrentSessions <= 0) throw new IllegalArgumentException("最大并发会话数必须大于0");
    }

    public static Quota of(int maxUsers, int maxCourses, int maxStorageGB,
                           int maxApiCallsPerMin, int maxConcurrentSessions) {
        return new Quota(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions);
    }

    public static Quota defaultQuota() {
        return new Quota(1000, 100, 10, 10000, 500);
    }

    public static Quota enterpriseQuota() {
        return new Quota(10000, 1000, 100, 100000, 5000);
    }

    public int getMaxUsers() { return maxUsers; }
    public int getMaxCourses() { return maxCourses; }
    public int getMaxStorageGB() { return maxStorageGB; }
    public int getMaxApiCallsPerMin() { return maxApiCallsPerMin; }
    public long getMaxStorageBytes() { return maxStorageGB * 1024L * 1024L * 1024L; }
    public int getMaxApiCallsPerMinute() { return maxApiCallsPerMin; }
    public int getMaxConcurrentSessions() { return maxConcurrentSessions; }
    public int getMaxKnowledgePoints() { return maxKnowledgePoints; }
    public int getMaxResourcesPerCourse() { return maxResourcesPerCourse; }

    public Quota withMaxUsers(int maxUsers) {
        return new Quota(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions);
    }

    public Quota withMaxCourses(int maxCourses) {
        return new Quota(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions);
    }

    public Quota withMaxStorageGB(int maxStorageGB) {
        return new Quota(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions);
    }

    public Quota withMaxApiCallsPerMin(int maxApiCallsPerMin) {
        return new Quota(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions);
    }

    public Quota withMaxConcurrentSessions(int maxConcurrentSessions) {
        return new Quota(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions);
    }

    public boolean canAllocateUser(int currentUsers) {
        return currentUsers < maxUsers;
    }

    public boolean canAllocateCourse(int currentCourses) {
        return currentCourses < maxCourses;
    }

    public boolean canAllocateStorage(int currentStorageGB) {
        return currentStorageGB < maxStorageGB;
    }

    /**
     * 检查存储是否超限
     *
     * @param usedBytes 已使用字节数
     * @return 是否超限
     */
    public boolean isStorageExceeded(long usedBytes) {
        long maxBytes = (long) maxStorageGB * 1024L * 1024L * 1024L;
        return usedBytes > maxBytes;
    }

    /**
     * 从JSON字符串反序列化为Quota对象
     *
     * @param json JSON字符串
     * @return Quota对象
     */
    public static Quota fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Quota>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的JSON格式: " + e.getMessage(), e);
        }
    }

    /**
     * 序列化为JSON字符串
     *
     * @return JSON字符串
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quota quota = (Quota) o;
        return maxUsers == quota.maxUsers &&
               maxCourses == quota.maxCourses &&
               maxStorageGB == quota.maxStorageGB &&
               maxApiCallsPerMin == quota.maxApiCallsPerMin &&
               maxConcurrentSessions == quota.maxConcurrentSessions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxUsers, maxCourses, maxStorageGB, maxApiCallsPerMin, maxConcurrentSessions);
    }

    @Override
    public String toString() {
        return "Quota{" +
               "maxUsers=" + maxUsers +
               ", maxCourses=" + maxCourses +
               ", maxStorageGB=" + maxStorageGB +
               ", maxApiCallsPerMin=" + maxApiCallsPerMin +
               ", maxConcurrentSessions=" + maxConcurrentSessions +
               '}';
    }
}