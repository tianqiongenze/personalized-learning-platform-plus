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

    boolean existsByCodeAndIdNot(String code, UUID id);

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


    Page<KnowledgePointEntity> findByCourseId(UUID courseId, Pageable pageable);

    List<KnowledgePointEntity> findByCourseId(UUID courseId);

    List<KnowledgePointEntity> findByChapterId(UUID chapterId);

    /**
     * 知识点名称/编码模糊检索(供知识图谱与 RAG 检索按实体名定位, 跨课程租户内)。
     */
    // JPQL(勿加 nativeQuery=true — 否则别名 kp 会被当列名, 报 Unknown column 'kp')
    @Query("SELECT kp FROM KnowledgePointEntity kp WHERE " +
           "LOWER(kp.code) LIKE LOWER(CONCAT('%', :entity, '%')) " +
           "OR LOWER(kp.name) LIKE LOWER(CONCAT('%', :entity, '%')) " +
           "ORDER BY kp.code ASC")
    List<KnowledgePointEntity> searchByEntity(@Param("entity") String entity, Pageable pageable);

    /**
     * 限定课程域的实体检索(knowledge_points 4500 万压测行, 双通配 LIKE 全表扫 50s+;
     * course_id IN(新课 45 门) 走 idx_kp_course_code 前导列, 毫秒级)。
     * KG 语义域=新课产业图谱, 压测 K12 数据天然不在域内。
     */
    @Query("SELECT kp FROM KnowledgePointEntity kp WHERE kp.courseId IN :cids AND " +
           "(LOWER(kp.code) LIKE LOWER(CONCAT('%', :entity, '%')) " +
           "OR LOWER(kp.name) LIKE LOWER(CONCAT('%', :entity, '%'))) " +
           "ORDER BY kp.code ASC")
    List<KnowledgePointEntity> searchByEntityInCourses(@Param("entity") String entity,
                                                       @Param("cids") List<UUID> courseIds,
                                                       Pageable pageable);
}