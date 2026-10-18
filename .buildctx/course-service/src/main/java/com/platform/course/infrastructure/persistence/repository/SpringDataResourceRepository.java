package com.platform.course.infrastructure.persistence.repository;

import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.model.valueobjects.ResourceStatus;
import com.platform.course.domain.model.valueobjects.ResourceType;
import com.platform.course.infrastructure.persistence.entity.ResourceEntity;
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
 * Spring Data JPA 资源仓储
 */
@Repository
public interface SpringDataResourceRepository extends JpaRepository<ResourceEntity, UUID> {

    Optional<ResourceEntity> findByCourseIdAndCode(UUID courseId, String code);

    boolean existsByCourseIdAndCode(UUID courseId, String code);

    List<ResourceEntity> findByCourseIdAndStatus(UUID courseId, ResourceStatus status);

    List<ResourceEntity> findByCourseIdAndType(UUID courseId, ResourceType type);

    List<ResourceEntity> findByChapterId(UUID chapterId);

    List<ResourceEntity> findByKpIdsContaining(UUID kpId);

    List<ResourceEntity> findByType(ResourceType type);

    @Query(value="SELECT r FROM resources r WHERE r.courseId = :courseId " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.type) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:type IS NULL OR r.type = :type) " +
           "AND (:chapterId IS NULL OR r.chapterId = :chapterId) " +
           "AND (:kpId IS NULL OR :kpId MEMBER OF r.kpIds) " +
           "ORDER BY r.createdAt DESC", nativeQuery=true)
    Page<ResourceEntity> findWithFilters(@Param("courseId") UUID courseId,
                                          @Param("status") ResourceStatus status,
                                          @Param("keyword") String keyword,
                                          @Param("type") ResourceType type,
                                          @Param("chapterId") UUID chapterId,
                                          @Param("kpId") UUID kpId,
                                          Pageable pageable);

    @Query(value="SELECT COUNT(r) FROM resources r WHERE r.courseId = :courseId " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(r.type) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:type IS NULL OR r.type = :type) " +
           "AND (:chapterId IS NULL OR r.chapterId = :chapterId) " +
           "AND (:kpId IS NULL OR :kpId MEMBER OF r.kpIds)", nativeQuery=true)
    long countWithFilters(@Param("courseId") UUID courseId,
                           @Param("status") ResourceStatus status,
                           @Param("keyword") String keyword,
                           @Param("type") ResourceType type,
                           @Param("chapterId") UUID chapterId,
                           @Param("kpId") UUID kpId);

@Query(value="SELECT r FROM resources r WHERE r.id IN :ids", nativeQuery=true)
    List<ResourceEntity> findByIds(@Param("ids") List<UUID> ids);
}