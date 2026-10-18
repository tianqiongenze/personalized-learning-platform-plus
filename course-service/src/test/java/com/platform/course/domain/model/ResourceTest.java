package com.platform.course.domain.model;

import com.platform.course.domain.event.ResourceCreatedEvent;
import com.platform.course.domain.event.ResourceDeletedEvent;
import com.platform.course.domain.event.ResourceUpdatedEvent;
import com.platform.course.domain.model.valueobjects.ChapterId;
import com.platform.course.domain.model.valueobjects.CourseId;
import com.platform.course.domain.model.valueobjects.KnowledgePointId;
import com.platform.course.domain.model.valueobjects.ResourceId;
import com.platform.course.domain.model.valueobjects.ResourceStatus;
import com.platform.course.domain.model.valueobjects.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Resource 聚合根测试")
class ResourceTest {

    private static final String TENANT = "tenant-1";
    private static final CourseId COURSE_ID = CourseId.generate();

    private Resource resource() {
        return Resource.create(COURSE_ID, TENANT, ChapterId.generate(), ResourceType.VIDEO,
                "教学视频", "描述", "https://cdn/v.mp4", "https://cdn/cover.png",
                Map.of("duration", 300), 2, Set.of("video"), "creator-1");
    }

    @Nested
    @DisplayName("创建资源")
    class Create {

        @Test
        @DisplayName("应创建草稿态资源")
        void shouldCreateDraftResource() {
            Resource resource = resource();

            assertThat(resource.getId()).isNotNull();
            assertThat(resource.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(resource.getType()).isEqualTo(ResourceType.VIDEO);
            assertThat(resource.getTitle()).isEqualTo("教学视频");
            assertThat(resource.getStatus()).isEqualTo(ResourceStatus.DRAFT);
            assertThat(resource.getVersion()).isEqualTo(1);
            assertThat(resource.getTags()).containsExactly("video");
            assertThat(resource.getMetadata()).containsEntry("duration", 300);
            assertThat(resource.isPublished()).isFalse();
            assertThat(resource.getDomainEvents().get(0)).isInstanceOf(ResourceCreatedEvent.class);
        }

        @Test
        @DisplayName("标题为空应抛出异常")
        void shouldThrowWhenTitleBlank() {
            assertThatThrownBy(() -> Resource.create(COURSE_ID, TENANT, null, ResourceType.DOCUMENT,
                    "  ", null, "url", null, null, 1, null, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("资源标题不能为空");
        }

        @Test
        @DisplayName("标题超长应抛出异常")
        void shouldThrowWhenTitleTooLong() {
            String tooLong = "标".repeat(301);
            assertThatThrownBy(() -> Resource.create(COURSE_ID, TENANT, null, ResourceType.DOCUMENT,
                    tooLong, null, "url", null, null, 1, null, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("资源标题不能超过300字符");
        }

        @Test
        @DisplayName("资源类型为空应抛出异常")
        void shouldThrowWhenTypeNull() {
            assertThatThrownBy(() -> Resource.create(COURSE_ID, TENANT, null, null,
                    "标题", null, "url", null, null, 1, null, "c"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("资源类型不能为空");
        }
    }

    @Nested
    @DisplayName("状态与版本")
    class StatusAndVersion {

        @Test
        @DisplayName("发布资源")
        void shouldPublish() {
            Resource resource = resource();
            resource.clearDomainEvents();

            resource.publish("admin");

            assertThat(resource.getStatus()).isEqualTo(ResourceStatus.PUBLISHED);
            assertThat(resource.isPublished()).isTrue();
            assertThat(resource.getDomainEvents().get(0)).isInstanceOf(ResourceUpdatedEvent.class);
        }

        @Test
        @DisplayName("更新信息应递增版本")
        void shouldUpdateInfoAndBumpVersion() {
            Resource resource = resource();

            resource.updateInfo("新标题", "新描述", "url2", "cover2", 3, Set.of("new"), "admin");

            assertThat(resource.getTitle()).isEqualTo("新标题");
            assertThat(resource.getDifficulty()).isEqualTo(3);
            assertThat(resource.getTags()).containsExactly("new");
            assertThat(resource.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("更新元数据应递增版本")
        void shouldUpdateMetadataAndBumpVersion() {
            Resource resource = resource();

            resource.updateMetadata(Map.of("a", 1), "admin");

            assertThat(resource.getMetadata()).containsEntry("a", 1);
            assertThat(resource.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("关联知识点应递增版本")
        void shouldAddKnowledgePointAndBumpVersion() {
            Resource resource = resource();
            KnowledgePointId kpId = KnowledgePointId.generate();

            resource.addKnowledgePoint(kpId, "admin");

            assertThat(resource.getKpIds()).containsExactly(kpId);
            assertThat(resource.getVersion()).isEqualTo(2);

            resource.removeKnowledgePoint(kpId, "admin");
            assertThat(resource.getKpIds()).isEmpty();
            assertThat(resource.getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("创建新版本应更新内容并递增版本")
        void shouldCreateNewVersion() {
            Resource resource = resource();

            resource.createNewVersion("v2标题", "v2描述", "url2", "cover2", Map.of("k", "v"), 4,
                    Set.of("v2"), "admin");

            assertThat(resource.getTitle()).isEqualTo("v2标题");
            assertThat(resource.getVersion()).isEqualTo(2);
            assertThat(resource.getTags()).containsExactly("v2");
        }
    }

    @Nested
    @DisplayName("删除")
    class Delete {

        @Test
        @DisplayName("删除资源")
        void shouldDelete() {
            Resource resource = resource();
            resource.clearDomainEvents();

            resource.delete("admin", "不再使用");

            assertThat(resource.getStatus()).isEqualTo(ResourceStatus.DELETED);
            assertThat(resource.isDeleted()).isTrue();
            assertThat(resource.getDeleteReason()).isEqualTo("不再使用");
            assertThat(resource.getDomainEvents().get(0)).isInstanceOf(ResourceDeletedEvent.class);
        }

        @Test
        @DisplayName("重复删除应抛出异常")
        void shouldThrowWhenDeleteTwice() {
            Resource resource = resource();
            resource.delete("admin", "第一次");

            assertThatThrownBy(() -> resource.delete("admin", "第二次"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("资源已被删除");
        }
    }

    @Nested
    @DisplayName("重建与相等性")
    class ReconstructAndEquality {

        @Test
        @DisplayName("从持久化重建资源")
        void shouldReconstruct() {
            ResourceId id = ResourceId.generate();
            Instant createdAt = Instant.now().minusSeconds(3600);

            Resource resource = Resource.reconstruct(id, COURSE_ID, TENANT, ChapterId.generate(),
                    ResourceType.DOCUMENT, "文档", "描述", "url", null, Map.of(), 1, Set.of(),
                    ResourceStatus.PUBLISHED, 3, "creator-1", createdAt, createdAt, null, null, null);

            assertThat(resource.getId()).isEqualTo(id);
            assertThat(resource.getStatus()).isEqualTo(ResourceStatus.PUBLISHED);
            assertThat(resource.getVersion()).isEqualTo(3);
            assertThat(resource.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("相同ID资源相等")
        void shouldBeEqualWithSameId() {
            ResourceId id = ResourceId.generate();
            Resource r1 = Resource.reconstruct(id, COURSE_ID, TENANT, null, ResourceType.VIDEO, "A", null,
                    "u", null, Map.of(), 1, Set.of(), ResourceStatus.DRAFT, 1, "c",
                    Instant.now(), Instant.now(), null, null, null);
            Resource r2 = Resource.reconstruct(id, COURSE_ID, TENANT, null, ResourceType.VIDEO, "B", null,
                    "u", null, Map.of(), 2, Set.of(), ResourceStatus.PUBLISHED, 2, "c",
                    Instant.now(), Instant.now(), null, null, null);

            assertThat(r1).isEqualTo(r2).hasSameHashCodeAs(r2);
        }
    }
}
