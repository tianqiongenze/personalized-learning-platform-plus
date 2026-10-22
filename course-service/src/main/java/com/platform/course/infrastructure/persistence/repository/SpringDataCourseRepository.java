package com.platform.course.infrastructure.persistence.repository;

import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.model.valueobjects.CourseStatus;
import com.platform.course.infrastructure.persistence.entity.CourseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA 课程仓储
 */
@Repository
public interface SpringDataCourseRepository extends JpaRepository<CourseEntity, UUID> {

    Optional<CourseEntity> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

@Query(value="SELECT COUNT(c) > 0 FROM courses c WHERE c.tenant_id = :tenantId AND c.code = :code AND c.id <> :id", nativeQuery=true)
    boolean existsByTenantIdAndCodeAndIdNot(@Param("tenantId") UUID tenantId, @Param("code") String code, @Param("id") UUID id);

    List<CourseEntity> findByTenantIdAndStatus(UUID tenantId, CourseStatus status);

    // D-04 修复(v2): TiDB 下 null 绑定参数即使用 SpEL 三元仍按 binary 发送, 与
    // utf8mb4 列比较报 "Cannot convert string from binary to utf8mb4"。
    // 彻底移除可选参数: 无过滤用 findByTenantId(Pageable), 有过滤才走 LIKE 版本, 全 ANSI。
    Page<CourseEntity> findByTenantId(UUID tenantId, Pageable pageable);

    @Query(value="SELECT c.* FROM courses c WHERE c.tenant_id = :tenantId " +
           "AND c.status = :status " +
           "AND (LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.subject) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY c.created_at DESC", nativeQuery=true)
    Page<CourseEntity> findWithFilters(@Param("tenantId") UUID tenantId,
                                        @Param("status") CourseStatus status,
                                        @Param("keyword") String keyword,
                                        @Param("subject") String subject,
                                        @Param("gradeLevel") String gradeLevel,
                                        Pageable pageable);

    long countByTenantId(UUID tenantId);

    @Query(value="SELECT COUNT(c) FROM courses c WHERE c.tenant_id = :tenantId " +
           "AND c.status = :status " +
           "AND (LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.subject) LIKE LOWER(CONCAT('%', :keyword, '%')))", nativeQuery=true)
    long countWithFilters(@Param("tenantId") UUID tenantId,
                           @Param("status") CourseStatus status,
                           @Param("keyword") String keyword,
                           @Param("subject") String subject,
                           @Param("gradeLevel") String gradeLevel);

    List<CourseEntity> findByCreatorId(String creatorId);

@Query(value="SELECT c.* FROM courses c WHERE c.id IN :ids", nativeQuery=true)
    List<CourseEntity> findByIds(@Param("ids") List<UUID> ids);
}