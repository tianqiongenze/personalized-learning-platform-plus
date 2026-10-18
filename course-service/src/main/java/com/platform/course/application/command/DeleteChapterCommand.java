package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record DeleteChapterCommand(
    @NotNull ChapterId chapterId,
    @NotBlank String deletedBy,
    @NotBlank String reason
) {}

/**
 * 创建知识点命令
 */
