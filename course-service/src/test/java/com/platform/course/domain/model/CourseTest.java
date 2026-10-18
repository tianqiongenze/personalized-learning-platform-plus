package com.platform.course.domain.model;

import com.platform.course.domain.event.CourseArchivedEvent;
import com.platform.course.domain.event.CourseCreatedEvent;
import com.platform.course.domain.event.CourseDeletedEvent;
import com.platform.course.domain.event.CoursePublishedEvent;
import com.platform.course.domain.event.CourseUpdatedEvent;
import com.platform.course.domain.event.DomainEvent;
import com.platform.course.domain.model.valueobjects.CourseId;
import com.platform.course.domain.model.valueobjects.CourseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Course 聚合根测试")
class CourseTest {

    private static final String TENANT = "tenant-1";

    private Course draftCourse() {
        return Course.create(TENANT, "math_101", "数学基础", "描述", "数学", "一年级", "2026春",
                "https://cdn/cover.png", Map.of("k", "v"), "creator-1");
    }

    @Nested
    @DisplayName("创建课程")
    class Create {

        @Test
        @DisplayName("应创建草稿态课程")
        void shouldCreateDraftCourse() {
            Course course = draftCourse();

            assertThat(course.getId()).isNotNull();
            assertThat(course.getTenantId()).isEqualTo(TENANT);
            assertThat(course.getCode()).isEqualTo("MATH_101");
            assertThat(course.getName()).isEqualTo("数学基础");
            assertThat(course.getSubject()).isEqualTo("数学");
            assertThat(course.getGradeLevel()).isEqualTo("一年级");
            assertThat(course.getSemester()).isEqualTo("2026春");
            assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
            assertThat(course.getCreatorId()).isEqualTo("creator-1");
            assertThat(course.getSettings()).containsEntry("k", "v");
            assertThat(course.isPublished()).isFalse();
            assertThat(course.getDomainEvents()).hasSize(1);
            assertThat(course.getDomainEvents().get(0)).isInstanceOf(CourseCreatedEvent.class);
        }

        @Test
        @DisplayName("编码为空应抛出异常")
        void shouldThrowWhenCodeBlank() {
            assertThatThrownBy(() -> Course.create(TENANT, "  ", "名称", null, null, null, null, null, null, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("课程编码不能为空");
        }

        @Test
        @DisplayName("编码格式非法应抛出异常")
        void shouldThrowWhenCodeIllegal() {
            assertThatThrownBy(() -> Course.create(TENANT, "bad-code!", "名称", null, null, null, null, null, null, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("课程编码必须为2-50位大写字母、数字或下划线");
        }

        @Test
        @DisplayName("名称为空应抛出异常")
        void shouldThrowWhenNameBlank() {
            assertThatThrownBy(() -> Course.create(TENANT, "math_101", " ", null, null, null, null, null, null, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("课程名称不能为空");
        }

        @Test
        @DisplayName("名称超长应抛出异常")
        void shouldThrowWhenNameTooLong() {
            String tooLong = "课".repeat(201);
            assertThatThrownBy(() -> Course.create(TENANT, "math_101", tooLong, null, null, null, null, null, null, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("课程名称不能超过200字符");
        }
    }

    @Nested
    @DisplayName("状态流转 DRAFT→PUBLISHED→ARCHIVED")
    class StatusTransition {

        @Test
        @DisplayName("草稿态可发布")
        void shouldPublishFromDraft() {
            Course course = draftCourse();
            course.clearDomainEvents();

            course.publish("admin");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(course.isPublished()).isTrue();
            assertThat(course.getPublishedAt()).isNotNull();
            assertThat(course.getDomainEvents()).hasSize(2);
            assertThat(course.getDomainEvents().get(0)).isInstanceOf(CoursePublishedEvent.class);
        }

        @Test
        @DisplayName("已发布课程再次发布保持发布态(幂等)")
        void shouldAllowRepublish() {
            Course course = draftCourse();
            course.publish("admin");

            course.publish("admin");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(course.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("已发布课程可归档")
        void shouldArchiveFromPublished() {
            Course course = draftCourse();
            course.publish("admin");
            course.clearDomainEvents();

            course.archive("admin");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
            assertThat(course.getArchivedAt()).isNotNull();
            assertThat(course.getDomainEvents().get(0)).isInstanceOf(CourseArchivedEvent.class);
        }

        @Test
        @DisplayName("草稿态不可直接归档")
        void shouldThrowWhenArchiveFromDraft() {
            Course course = draftCourse();

            assertThatThrownBy(() -> course.archive("admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("课程状态不允许从 草稿 流转到 已归档");
        }

        @Test
        @DisplayName("归档态可恢复发布")
        void shouldRestorePublishFromArchived() {
            Course course = draftCourse();
            course.publish("admin");
            course.archive("admin");

            course.restorePublish("admin");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(course.getArchivedAt()).isNull();
        }

        @Test
        @DisplayName("已删除课程不可恢复发布")
        void shouldThrowWhenRestorePublishFromDeleted() {
            Course course = draftCourse();
            course.delete("admin", "不再使用");

            assertThatThrownBy(() -> course.restorePublish("admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("课程状态不允许从 已删除 流转到 已发布");
        }

        @Test
        @DisplayName("任意状态可删除")
        void shouldDeleteFromDraft() {
            Course course = draftCourse();
            course.clearDomainEvents();

            course.delete("admin", "不再使用");

            assertThat(course.getStatus()).isEqualTo(CourseStatus.DELETED);
            assertThat(course.isDeleted()).isTrue();
            assertThat(course.getDeletedAt()).isNotNull();
            assertThat(course.getDeleteReason()).isEqualTo("不再使用");
            assertThat(course.getDomainEvents().get(0)).isInstanceOf(CourseDeletedEvent.class);
        }

        @Test
        @DisplayName("重复删除应抛出异常")
        void shouldThrowWhenDeleteTwice() {
            Course course = draftCourse();
            course.delete("admin", "第一次");

            assertThatThrownBy(() -> course.delete("admin", "第二次"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("课程已被删除");
        }
    }

    @Nested
    @DisplayName("信息维护")
    class InfoMaintenance {

        @Test
        @DisplayName("更新基本信息")
        void shouldUpdateInfo() {
            Course course = draftCourse();
            course.clearDomainEvents();

            course.updateInfo("新名称", "新描述", "语文", "二年级", "2026秋", "cover2", "admin");

            assertThat(course.getName()).isEqualTo("新名称");
            assertThat(course.getSubject()).isEqualTo("语文");
            assertThat(course.getDomainEvents().get(0)).isInstanceOf(CourseUpdatedEvent.class);
        }

        @Test
        @DisplayName("草稿态可更新编码")
        void shouldUpdateCodeForDraft() {
            Course course = draftCourse();

            course.updateCode("math_202", "admin");

            assertThat(course.getCode()).isEqualTo("MATH_202");
        }

        @Test
        @DisplayName("非草稿态禁止更新编码")
        void shouldThrowWhenUpdateCodeNotDraft() {
            Course course = draftCourse();
            course.publish("admin");

            assertThatThrownBy(() -> course.updateCode("math_202", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅草稿态课程允许修改编码");
        }

        @Test
        @DisplayName("更新设置应整体替换")
        void shouldUpdateSettings() {
            Course course = draftCourse();

            course.updateSettings(Map.of("a", 1), "admin");

            assertThat(course.getSettings()).containsOnlyKeys("a");
        }
    }

    @Nested
    @DisplayName("重建与相等性")
    class ReconstructAndEquality {

        @Test
        @DisplayName("从持久化重建课程")
        void shouldReconstruct() {
            CourseId id = CourseId.generate();
            Instant createdAt = Instant.now().minusSeconds(7200);
            Instant publishedAt = Instant.now().minusSeconds(3600);

            Course course = Course.reconstruct(id, TENANT, "math_101", "数学", "描述", "数学", "一年级", "2026春",
                    CourseStatus.PUBLISHED, null, Map.of(), "creator-1",
                    createdAt, publishedAt, publishedAt, null, null, null, null);

            assertThat(course.getId()).isEqualTo(id);
            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
            assertThat(course.getCreatedAt()).isEqualTo(createdAt);
            assertThat(course.getPublishedAt()).isEqualTo(publishedAt);
            assertThat(course.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("相同ID课程相等")
        void shouldBeEqualWithSameId() {
            CourseId id = CourseId.generate();
            Course c1 = Course.reconstruct(id, TENANT, "a_1", "A", null, null, null, null,
                    CourseStatus.DRAFT, null, Map.of(), "c", Instant.now(), Instant.now(), null, null, null, null, null);
            Course c2 = Course.reconstruct(id, TENANT, "b_2", "B", null, null, null, null,
                    CourseStatus.ARCHIVED, null, Map.of(), "c", Instant.now(), Instant.now(), null, null, null, null, null);

            assertThat(c1).isEqualTo(c2).hasSameHashCodeAs(c2);
        }

        @Test
        @DisplayName("不同ID课程不相等")
        void shouldNotBeEqualWithDifferentId() {
            List<Course> courses = List.of(draftCourse(), draftCourse());

            assertThat(courses.get(0)).isNotEqualTo(courses.get(1));
            assertThat(courses.get(0).getDomainEvents()).isNotEmpty();
        }
    }
}
