package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record AddChapterKnowledgePointCommand(
    @NotNull ChapterId chapterId,
    @NotNull KnowledgePointId kpId,
    @NotBlank String updatedBy
) {}

/**
 * 移除章节知识点命令
 */
