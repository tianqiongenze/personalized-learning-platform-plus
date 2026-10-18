package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record UpdateChapterCodeCommand(
    @NotNull ChapterId chapterId,
    @NotBlank @Size(min = 2, max = 50) String code,
    @NotBlank String updatedBy
) {}

/**
 * 更新章节排序命令
 */
