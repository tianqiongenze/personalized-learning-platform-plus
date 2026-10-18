package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record UpdateKnowledgePointMetadataCommand(
    @NotNull KnowledgePointId kpId,
    @NotNull Map<String, Object> metadata,
    @NotBlank String updatedBy
) {}

/**
 * 删除知识点命令
 */
