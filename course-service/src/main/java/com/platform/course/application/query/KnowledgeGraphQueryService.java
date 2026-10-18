package com.platform.course.application.query;

import java.util.List;

/**
 * 知识图谱查询服务(读侧 CQRS)。
 * 供 agent-service 的 query_knowledge_graph 工具与前端知识图谱可视化使用。
 */
public interface KnowledgeGraphQueryService {

    /**
     * 按实体名检索知识点, 返回一跳关联(先修 / 相关知识点)。
     *
     * @param entity 知识点名称/编码(或知识点 ID)
     * @param hops   关联跳数(1-3), 超出范围收敛为 2
     */
    KnowledgeGraphDto findGraph(String entity, int hops);

    record GraphNode(String id, String name, String code, String courseId,
                     int difficulty, String type) {}

    record GraphEdge(String source, String target, String relation) {}

    record KnowledgeGraphDto(String entity, int hops, List<GraphNode> nodes, List<GraphEdge> edges) {
        public static KnowledgeGraphDto empty(String entity, int hops) {
            return new KnowledgeGraphDto(entity, hops, List.of(), List.of());
        }
    }
}
