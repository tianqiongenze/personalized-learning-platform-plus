package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record UpdateChapterInfoCommand(
    @NotNull ChapterId chapterId,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    int difficulty,
    int estimatedHours,
    @NotBlank String updatedBy
) {}

/**
 * 更新章节编码命令
 */
