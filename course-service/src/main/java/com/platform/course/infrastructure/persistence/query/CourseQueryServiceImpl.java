package com.platform.course.infrastructure.persistence.query;

import org.springframework.cache.annotation.Cacheable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.course.application.query.*;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.infrastructure.persistence.entity.CourseEntity;
import com.platform.course.infrastructure.persistence.repository.SpringDataCourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Course 域查询实现(CQRS 读侧) */
@Service
@Transactional(readOnly = true)
public class CourseQueryServiceImpl implements CourseQueryService {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final SpringDataCourseRepository courseRepository;

    public CourseQueryServiceImpl(SpringDataCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    @Cacheable(cacheManager = "multiLevelCacheManager", cacheNames = "courseById", key = "#id.value.toString()", unless = "#result == null")
    public CourseDetailDto findById(CourseId id) {
        return courseRepository.findById(id.getValue()).map(CourseQueryServiceImpl::toDetail)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在: " + id));
    }

    @Override
    @Cacheable(cacheManager = "multiLevelCacheManager", cacheNames = "courseByCode", key = "#tenantId + ':' + #code", unless = "#result == null")
    public CourseDetailDto findByCode(String tenantId, String code) {
        return courseRepository.findByTenantIdAndCode(UUID.fromString(tenantId), code)
                .map(CourseQueryServiceImpl::toDetail)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在: " + code));
    }

    @Override
    public PageResult<CourseListDto> findPage(CoursePageQuery query) {
        UUID tenantUuid = UUID.fromString(query.tenantId());
        PageRequest pr = PageRequest.of(Math.max(query.page(), 0), Math.max(query.size(), 1));
        // D-04 修复(v2): 无过滤条件时不传可选参数, 避免 TiDB 下 null 绑定参数 binary 转换错误
        Page<CourseEntity> page = (query.keyword() == null || query.keyword().isBlank())
                ? courseRepository.findByTenantId(tenantUuid, pr)
                : courseRepository.findWithFilters(tenantUuid, null, query.keyword(), null, null, pr);
        List<CourseListDto> items = page.getContent().stream()
                .map(CourseQueryServiceImpl::toList).collect(Collectors.toList());
        return new PageResult<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Override
    public List<CourseSimpleDto> findByCreatorId(String creatorId) {
        return courseRepository.findByCreatorId(creatorId).stream()
                .map(c -> new CourseSimpleDto(c.getId().toString(), c.getCode(), c.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public CourseStatisticsDto statistics(String tenantId) {
        return new CourseStatisticsDto(courseRepository.count(), 0, 0, 0, 0);
    }

    static Map<String, Object> parseJson(String json) {
        try {
            return json == null ? Map.of() : MAPPER.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static CourseDetailDto toDetail(CourseEntity c) {
        return new CourseDetailDto(c.getId().toString(), c.getTenantId().toString(), c.getCode(),
                c.getName(), c.getDescription(), c.getSubject(), c.getGradeLevel(), c.getSemester(),
                c.getStatus() == null ? null : c.getStatus().name(), c.getCoverUrl(),
                parseJson(c.getSettingsJson()), c.getCreatorId(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getPublishedAt(), c.getArchivedAt(),
                c.getDeletedAt(), c.getDeletedBy(), c.getDeleteReason());
    }

    private static CourseListDto toList(CourseEntity c) {
        return new CourseListDto(c.getId().toString(), c.getTenantId().toString(), c.getCode(),
                c.getName(), c.getSubject(), c.getGradeLevel(), c.getSemester(),
                c.getStatus() == null ? null : c.getStatus().name(), c.getCoverUrl(),
                c.getCreatorId(), c.getCreatedAt());
    }
}
