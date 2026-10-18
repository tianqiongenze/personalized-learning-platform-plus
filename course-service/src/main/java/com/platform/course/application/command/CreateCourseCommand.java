package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record CreateCourseCommand(
    @NotBlank @Size(min = 2, max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @NotBlank @Size(max = 50) String subject,
    @NotBlank @Size(max = 20) String gradeLevel,
    @NotBlank @Size(max = 20) String semester,
    String coverUrl,
    Map<String, Object> settings,
    @NotBlank String creatorId
) {}

/**
 * 更新课程基本信息命令
 */
