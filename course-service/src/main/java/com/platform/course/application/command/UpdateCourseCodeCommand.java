package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record UpdateCourseCodeCommand(
    @NotNull CourseId courseId,
    @NotBlank @Size(min = 2, max = 50) String code,
    @NotBlank String updatedBy
) {}

/**
 * 更新课程设置命令
 */
