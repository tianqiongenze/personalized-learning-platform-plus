package com.platform.course.application.command;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

/**
 * 创建课程命令
 */
public record CreateResourceVersionCommand(
    @NotNull ResourceId resourceId,
    @NotBlank @Size(max = 300) String title,
    @Size(max = 2000) String description,
    String contentUrl,
    String coverUrl,
    Map<String, Object> metadata,
    int difficulty,
    Set<String> tags,
    @NotBlank String updatedBy
) {}

/**
/**

 * 删除资源命令
 */
