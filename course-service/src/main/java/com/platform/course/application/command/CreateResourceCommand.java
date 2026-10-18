package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record CreateResourceCommand(
    @NotNull CourseId courseId,
    ChapterId chapterId,
    @NotNull ResourceType type,
    @NotBlank @Size(max = 300) String title,
    @Size(max = 2000) String description,
    @NotBlank String contentUrl,
    String coverUrl,
    Map<String, Object> metadata,
    int difficulty,
    Set<String> tags,
    @NotBlank String creatorId
) {}

/**
 * 更新资源信息命令
 */
