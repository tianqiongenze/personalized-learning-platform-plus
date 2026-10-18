package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record UpdateResourceInfoCommand(
    @NotNull ResourceId resourceId,
    @NotBlank @Size(max = 300) String title,
    @Size(max = 2000) String description,
    String contentUrl,
    String coverUrl,
    int difficulty,
    Set<String> tags,
    @NotBlank String updatedBy
) {}

/**
 * 更新资源元数据命令
 */
