package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record RestoreCourseCommand(
    @NotNull CourseId courseId,
    @NotBlank String restoredBy
) {}

/**
 * 删除课程命令
 */
