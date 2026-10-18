package com.platform.course.infrastructure.persistence.repository;

import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.infrastructure.persistence.entity.KnowledgePointEntity;
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
 * Spring Data JPA 知识点仓储
 */
@Repository
public interface SpringDataKnowledgePointRepository extends JpaRepository<KnowledgePointEntity, UUID> {

    Optional<KnowledgePointEntity> findByCourseIdAndCode(UUID courseId, String code);

    boolean existsByCourseIdAndCode(UUID courseId, String code);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(e) > 0 FROM KnowledgePointEntity e WHERE e.courseId = :courseId AND e.code = :code AND e.id <> :id")
    boolean existsByCourseIdAndCodeAndIdNotCustom(@org.springframework.data.repository.query.Param("courseId") UUID courseId, @org.springframework.data.repository.query.Param("code") String code, @org.springframework.data.repository.query.Param("id") UUID id);

    List<KnowledgePointEntity> findByCourseIdAndChapterId(UUID courseId, UUID chapterId);

    @Query(value="SELECT kp FROM knowledge_points kp WHERE kp.courseId = :courseId " +
           "AND (:status IS NULL OR kp.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(kp.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(kp.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY kp.code ASC", nativeQuery=true)
    Page<KnowledgePointEntity> findWithFilters(@Param("courseId") UUID courseId,
                                                @Param("status") String status,
                                                @Param("keyword") String keyword,
                                                @Param("chapterId") UUID chapterId,
                                                Pageable pageable);

    @Query(value="SELECT COUNT(kp) FROM knowledge_points kp WHERE kp.courseId = :courseId " +
           "AND (:status IS NULL OR kp.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(kp.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(kp.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:chapterId IS NULL OR kp.chapterId = :chapterId)", nativeQuery=true)
    long countWithFilters(@Param("courseId") UUID courseId,
                           @Param("status") String status,
                           @Param("keyword") String keyword,
                           @Param("chapterId") UUID chapterId);

@Query(value="SELECT kp FROM knowledge_points kp WHERE kp.id IN :ids", nativeQuery=true)
    List<KnowledgePointEntity> findByIds(@Param("ids") List<UUID> ids);
}