package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record UpdateKnowledgePointCodeCommand(
    @NotNull KnowledgePointId kpId,
    @NotBlank @Size(min = 2, max = 50) String code,
    @NotBlank String updatedBy
) {}

/**
 * 添加前置知识点命令
 */
