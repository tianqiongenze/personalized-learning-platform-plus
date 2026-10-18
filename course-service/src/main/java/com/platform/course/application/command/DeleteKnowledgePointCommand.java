package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record DeleteKnowledgePointCommand(
    @NotNull KnowledgePointId kpId,
    @NotBlank String deletedBy,
    @NotBlank String reason
) {}

/**
 * 创建资源命令
 */
