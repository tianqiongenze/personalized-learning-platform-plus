package com.platform.course.infrastructure.persistence.repository;

import com.platform.course.domain.model.KnowledgePoint;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.repository.KnowledgePointRepository;
import com.platform.course.infrastructure.persistence.entity.KnowledgePointEntity;
import com.platform.course.infrastructure.persistence.mapper.KnowledgePointEntityMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识点仓储实现
 */
@Repository
public class KnowledgePointRepositoryImpl implements KnowledgePointRepository {

    private final SpringDataKnowledgePointRepository jpaRepository;
    private final KnowledgePointEntityMapper mapper;

    public KnowledgePointRepositoryImpl(SpringDataKnowledgePointRepository jpaRepository, KnowledgePointEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public KnowledgePoint save(KnowledgePoint kp) {
        KnowledgePointEntity entity = mapper.toEntity(kp);
        KnowledgePointEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<KnowledgePoint> findById(KnowledgePointId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<KnowledgePoint> findByCode(CourseId courseId, String code) {
        return jpaRepository.findByCourseIdAndCode(courseId.getValue(), code)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(CourseId courseId, String code) {
        return jpaRepository.existsByCourseIdAndCode(courseId.getValue(), code);
    }

    @Override
    public List<KnowledgePoint> findByCourseId(CourseId courseId, int page, int size, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
        
        Page<KnowledgePointEntity> result = jpaRepository.findWithFilters(
            courseId.getValue(), status, keyword, null, pageable);
        
        return result.getContent().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByCourseId(CourseId courseId, String status, String keyword) {
        return jpaRepository.countWithFilters(courseId.getValue(), status, keyword, null);
    }

    @Override
    public List<KnowledgePoint> findByChapterId(ChapterId chapterId) {
        return jpaRepository.findByCourseIdAndChapterId(null, chapterId.getValue()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgePoint> findByIds(List<KnowledgePointId> ids) {
        List<UUID> uuids = ids.stream().map(KnowledgePointId::getValue).collect(Collectors.toList());
        return jpaRepository.findAllById(uuids).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(KnowledgePointId id) {
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, KnowledgePointId id) {
        return jpaRepository.findAll().stream()
            .anyMatch(e -> code.equals(e.getCode()) && !id.getValue().equals(e.getKpId()));
    }

    @Override
    public boolean existsByCodeAndCourseId(String code, CourseId courseId) {
        return jpaRepository.findAll().stream()
            .anyMatch(e -> code.equals(e.getCode()) && courseId.getValue().equals(e.getCourseId()));
    }

    @Override
    public boolean existsByCodeAndCourseIdAndIdNot(String code, CourseId courseId, KnowledgePointId id) {
        return jpaRepository.findAll().stream()
            .anyMatch(e -> code.equals(e.getCode()) && courseId.getValue().equals(e.getCourseId())
                && !id.getValue().equals(e.getKpId()));
    }
}