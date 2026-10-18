package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record CreateKnowledgePointCommand(
    @NotNull CourseId courseId,
    @NotBlank @Size(min = 2, max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    ChapterId chapterId,
    int difficulty,
    @NotBlank String createdBy
) {}

/**
 * 更新知识点信息命令
 */
