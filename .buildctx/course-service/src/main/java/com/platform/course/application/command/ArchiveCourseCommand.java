package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record ArchiveCourseCommand(
    @NotNull CourseId courseId,
    @NotBlank String archivedBy
) {}

/**
 * 恢复发布命令
 */
