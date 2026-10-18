package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record UpdateKnowledgePointInfoCommand(
    @NotNull KnowledgePointId kpId,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    int difficulty,
    @NotBlank String updatedBy
) {}

/**
 * 更新知识点编码命令
 */
