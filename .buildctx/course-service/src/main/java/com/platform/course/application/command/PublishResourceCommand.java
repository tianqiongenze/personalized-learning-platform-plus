package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record PublishResourceCommand(
    @NotNull ResourceId resourceId,
    @NotBlank String publishedBy
) {}

/**
 * 创建资源新版本命令
 */
