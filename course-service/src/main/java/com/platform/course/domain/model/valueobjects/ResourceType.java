package com.platform.course.domain.model.valueobjects;

/**
 * 资源类型枚举
 */
public enum ResourceType {
    VIDEO("视频"),
    DOCUMENT("文档"),
    EXAM("考试"),
    SCORM("SCORM包"),
    H5P("H5P互动内容"),
    AUDIO("音频"),
    SIMULATION("虚拟仿真"),
    LINK("外部链接"),
    LIVE("直播回放");

    private final String displayName;

    ResourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
