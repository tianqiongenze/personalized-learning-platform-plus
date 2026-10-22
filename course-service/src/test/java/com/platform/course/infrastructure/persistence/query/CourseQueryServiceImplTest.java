package com.platform.course.infrastructure.persistence.query;

import com.platform.course.application.query.CourseDetailDto;
import com.platform.course.application.query.CourseListDto;
import com.platform.course.application.query.CoursePageQuery;
import com.platform.course.application.query.CourseSimpleDto;
import com.platform.course.application.query.CourseStatisticsDto;
import com.platform.course.application.query.PageResult;
import com.platform.course.domain.model.valueobjects.CourseId;
import com.platform.course.domain.model.valueobjects.CourseStatus;
import com.platform.course.infrastructure.persistence.entity.CourseEntity;
import com.platform.course.infrastructure.persistence.repository.SpringDataCourseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseQueryServiceImpl 测试")
class CourseQueryServiceImplTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private SpringDataCourseRepository courseRepository;

    @InjectMocks
    private CourseQueryServiceImpl service;

    private CourseEntity entity(UUID id) {
        return CourseEntity.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .code("MATH_101")
                .name("数学基础")
                .subject("数学")
                .gradeLevel("一年级")
                .semester("2026春")
                .status(CourseStatus.PUBLISHED)
                .settingsJson("{\"k\":\"v\"}")
                .creatorId("creator-1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("findById 命中应映射为详情")
    void shouldFindById() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(entity(id)));

        CourseDetailDto dto = service.findById(CourseId.of(id));

        assertThat(dto.code()).isEqualTo("MATH_101");
        assertThat(dto.name()).isEqualTo("数学基础");
        assertThat(dto.status()).isEqualTo("PUBLISHED");
        assertThat(dto.settings()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("findById 未命中应抛出异常")
    void shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(CourseId.of(id)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("课程不存在");
    }

    @Test
    @DisplayName("findByCode 应映射为详情")
    void shouldFindByCode() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findByTenantIdAndCode(TENANT_ID, "MATH_101"))
                .thenReturn(Optional.of(entity(id)));

        CourseDetailDto dto = service.findByCode(TENANT_ID.toString(), "MATH_101");

        assertThat(dto.id()).isEqualTo(id.toString());
    }

    @Test
    @DisplayName("findByCreatorId 应返回精简列表")
    void shouldFindByCreatorId() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findByCreatorId("creator-1")).thenReturn(List.of(entity(id)));

        List<CourseSimpleDto> result = service.findByCreatorId("creator-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("MATH_101");
    }

    @Test
    @DisplayName("findPage 应分页映射")
    void shouldFindPage() {
        UUID id = UUID.randomUUID();
        Page<CourseEntity> page = new PageImpl<>(List.of(entity(id)));
        when(courseRepository.findByTenantId(eq(TENANT_ID), any(Pageable.class))).thenReturn(page);

        CoursePageQuery query = new CoursePageQuery(TENANT_ID.toString(), 0, 20, null, null, null, null,
                "createdAt", "desc");
        PageResult<CourseListDto> result = service.findPage(query);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).code()).isEqualTo("MATH_101");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("statistics 应返回总数")
    void shouldStatistics() {
        when(courseRepository.count()).thenReturn(7L);

        CourseStatisticsDto dto = service.statistics(TENANT_ID.toString());

        assertThat(dto.total()).isEqualTo(7L);
    }

    @Test
    @DisplayName("parseJson 对空串返回空 Map")
    void shouldParseJsonSafely() {
        assertThat(CourseQueryServiceImpl.parseJson(null)).isEmpty();
        assertThat(CourseQueryServiceImpl.parseJson("not-json")).isEmpty();
        assertThat(CourseQueryServiceImpl.parseJson("{\"a\":1}")).containsEntry("a", 1);
    }
}
