package com.platform.course.infrastructure.persistence.repository;

import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.model.valueobjects.ChapterStatus;
import com.platform.course.infrastructure.persistence.entity.ChapterEntity;
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
 * Spring Data JPA 章节仓储
 */
@Repository
public interface SpringDataChapterRepository extends JpaRepository<ChapterEntity, UUID> {

    Optional<ChapterEntity> findByCourseIdAndCode(UUID courseId, String code);

    boolean existsByCourseIdAndCode(UUID courseId, String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(e) > 0 FROM ChapterEntity e WHERE e.courseId = :courseId AND e.code = :code AND e.id <> :id")
    boolean existsByCourseIdAndCodeAndIdNotCustom(@org.springframework.data.repository.query.Param("courseId") UUID courseId, @org.springframework.data.repository.query.Param("code") String code, @org.springframework.data.repository.query.Param("id") UUID id);

    List<ChapterEntity> findByCourseIdAndStatus(UUID courseId, ChapterStatus status);

    List<ChapterEntity> findByParentId(UUID parentId);

    @Query(value="SELECT c FROM chapters c WHERE c.courseId = :courseId " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY c.order ASC", nativeQuery=true)
    Page<ChapterEntity> findWithFilters(@Param("courseId") UUID courseId,
                                         @Param("status") ChapterStatus status,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);

    @Query(value="SELECT COUNT(c) FROM chapters c WHERE c.courseId = :courseId " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')))", nativeQuery=true)
    long countWithFilters(@Param("courseId") UUID courseId,
                           @Param("status") ChapterStatus status,
                           @Param("keyword") String keyword);


@Query(value="SELECT ch FROM chapters ch WHERE ch.id IN :ids", nativeQuery=true)
    List<ChapterEntity> findByIds(@Param("ids") List<UUID> ids);


    Page<ChapterEntity> findByCourseId(UUID courseId, Pageable pageable);

    List<ChapterEntity> findByCourseId(UUID courseId);
}