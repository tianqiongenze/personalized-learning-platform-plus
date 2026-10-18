package com.platform.course.infrastructure.persistence.repository;

import com.platform.course.domain.model.Chapter;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.repository.ChapterRepository;
import com.platform.course.infrastructure.persistence.entity.ChapterEntity;
import com.platform.course.infrastructure.persistence.mapper.ChapterEntityMapper;
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
 * 章节仓储实现
 */
@Repository
public class ChapterRepositoryImpl implements ChapterRepository {

    private final SpringDataChapterRepository jpaRepository;
    private final ChapterEntityMapper mapper;

    public ChapterRepositoryImpl(SpringDataChapterRepository jpaRepository, ChapterEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Chapter save(Chapter chapter) {
        ChapterEntity entity = mapper.toEntity(chapter);
        ChapterEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Chapter> findById(ChapterId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Chapter> findByCode(CourseId courseId, String code) {
        return jpaRepository.findByCourseIdAndCode(courseId.getValue(), code)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(CourseId courseId, String code) {
        return jpaRepository.existsByCourseIdAndCode(courseId.getValue(), code);
    }

    @Override
    public List<Chapter> findByCourseId(CourseId courseId, int page, int size, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "order"));
        
        ChapterStatus statusEnum = status != null ? ChapterStatus.valueOf(status.toUpperCase()) : null;
        Page<ChapterEntity> result = jpaRepository.findWithFilters(
            courseId.getValue(), statusEnum, keyword, pageable);
        
        return result.getContent().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByCourseId(CourseId courseId, String status, String keyword) {
        ChapterStatus statusEnum = status != null ? ChapterStatus.valueOf(status.toUpperCase()) : null;
        return jpaRepository.countWithFilters(courseId.getValue(), statusEnum, keyword);
    }

    @Override
    public List<Chapter> findByParentId(ChapterId parentId) {
        return jpaRepository.findByParentId(parentId.getValue()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Chapter> findByIds(List<ChapterId> ids) {
        List<UUID> uuids = ids.stream().map(ChapterId::getValue).collect(Collectors.toList());
        return jpaRepository.findAllById(uuids).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(ChapterId id) {
        jpaRepository.deleteById(id.getValue());
    }

    // 修复(性能): 原实现用 findAll().stream() 做存在性检查 = 全表扫描,
    // 在 1 亿级数据下是灾难。改为数据库层计数查询(派生方法/自定义 JPQL)。
    // 注意: code 全局唯一性原逻辑实为"同课程内 code 唯一", 统一收敛为课程内校验。
    @Override
    public boolean existsByCodeAndIdNot(String code, ChapterId id) {
        // 兼容旧签名: 无 courseId 上下文时按 code 全局计数(应已由上层传入 courseId)
        return jpaRepository.existsByCodeAndIdNot(code, id.getValue());
    }

    @Override
    public boolean existsByCodeAndCourseId(String code, CourseId courseId) {
        return jpaRepository.existsByCourseIdAndCode(courseId.getValue(), code);
    }

    @Override
    public boolean existsByCodeAndCourseIdAndIdNot(String code, CourseId courseId, ChapterId id) {
        return jpaRepository.existsByCourseIdAndCodeAndIdNotCustom(courseId.getValue(), code, id.getValue());
    }
}