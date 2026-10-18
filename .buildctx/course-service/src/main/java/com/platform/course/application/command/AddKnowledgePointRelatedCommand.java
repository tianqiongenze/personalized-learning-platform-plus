package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record AddKnowledgePointRelatedCommand(
    @NotNull KnowledgePointId kpId,
    @NotNull KnowledgePointId relatedId,
    @NotBlank String updatedBy
) {}

/**
 * 移除关联知识点命令
 */
