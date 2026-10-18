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

    @Query(value="SELECT c.* FROM courses c WHERE c.tenant_id = :tenantId " +
           "AND (CAST(:status AS char) IS NULL OR c.status = :status) " +
           "AND (CAST(:keyword AS char) IS NULL OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.subject) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY c.created_at DESC", nativeQuery=true)
    Page<CourseEntity> findWithFilters(@Param("tenantId") UUID tenantId,
                                        @Param("status") CourseStatus status,
                                        @Param("keyword") String keyword,
                                        @Param("subject") String subject,
                                        @Param("gradeLevel") String gradeLevel,
                                        Pageable pageable);

    @Query(value="SELECT COUNT(c) FROM courses c WHERE c.tenant_id = :tenantId " +
           "AND (CAST(:status AS char) IS NULL OR c.status = :status) " +
           "AND (CAST(:keyword AS char) IS NULL OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
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