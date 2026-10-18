package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record UpdateCourseInfoCommand(
    @NotNull CourseId courseId,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @NotBlank @Size(max = 50) String subject,
    @NotBlank @Size(max = 20) String gradeLevel,
    @NotBlank @Size(max = 20) String semester,
    String coverUrl,
    @NotBlank String updatedBy
) {}

/**
 * 更新课程编码命令
 */
