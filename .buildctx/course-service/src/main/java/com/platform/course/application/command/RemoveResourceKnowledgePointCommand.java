package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record RemoveResourceKnowledgePointCommand(
    @NotNull ResourceId resourceId,
    @NotNull KnowledgePointId kpId,
    @NotBlank String updatedBy
) {}

/**
 * 发布资源命令
 */
