package com.platform.iam.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;

/**
 * 安全上下文工具类
 */
public final class SecurityContextUtil {

    private SecurityContextUtil() {}

    /**
     * 获取当前认证用户ID
     */
    public static Optional<String> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String) {
            return Optional.of((String) auth.getPrincipal());
        }
        return Optional.empty();
    }

    /**
     * 获取当前租户ID
     */
    public static Optional<String> getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails) {
            return Optional.of(((JwtAuthenticationFilter.JwtAuthenticationDetails) auth.getDetails()).getTenantId());
        }
        return Optional.empty();
    }

    /**
     * 获取当前用户名
     */
    public static Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails) {
            return Optional.of(((JwtAuthenticationFilter.JwtAuthenticationDetails) auth.getDetails()).getUsername());
        }
        return Optional.empty();
    }

    /**
     * 获取当前用户角色
     */
    public static String[] getCurrentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails) {
            return ((JwtAuthenticationFilter.JwtAuthenticationDetails) auth.getDetails()).getRoles();
        }
        return new String[0];
    }

    /**
     * 检查是否拥有指定角色
     */
    public static boolean hasRole(String role) {
        String[] roles = getCurrentRoles();
        for (String r : roles) {
            if (r.equals(role)) return true;
        }
        return false;
    }

    /**
     * 检查是否拥有任意指定角色
     */
    public static boolean hasAnyRole(String... roles) {
        String[] currentRoles = getCurrentRoles();
        for (String current : currentRoles) {
            for (String required : roles) {
                if (current.equals(required)) return true;
            }
        }
        return false;
    }

    /**
     * 清除安全上下文
     */
    public static void clearContext() {
        SecurityContextHolder.clearContext();
    }
}