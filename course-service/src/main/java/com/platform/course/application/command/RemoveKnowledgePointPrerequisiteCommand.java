package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record RemoveKnowledgePointPrerequisiteCommand(
    @NotNull KnowledgePointId kpId,
    @NotNull KnowledgePointId prerequisiteId,
    @NotBlank String updatedBy
) {}

/**
 * 添加关联知识点命令
 */
