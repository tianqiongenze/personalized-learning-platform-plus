package com.platform.course.infrastructure.persistence.query;

import com.platform.course.application.query.KnowledgeGraphQueryService;
import com.platform.course.infrastructure.persistence.entity.KnowledgePointEntity;
import com.platform.course.infrastructure.persistence.repository.SpringDataKnowledgePointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 知识图谱查询实现: 实体名 -> 知识点 -> 一跳关联(先修/相关)。
 * 说明: 当前图谱边来自 course 域的知识点前置/关联关系(关系型建模);
 * 重庆"33618"产业图谱(NebulaGraph kg33618)由 agent-service 侧查询,
 * 二者经同一 /knowledge/graph 契约对外暴露, 见 docs/IP_STRATEGY_PLAN。
 */
@Service
@Transactional(readOnly = true)
public class KnowledgeGraphQueryServiceImpl implements KnowledgeGraphQueryService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphQueryServiceImpl.class);
    private static final int MAX_SEEDS = 20;
    private static final int MAX_FANOUT = 50;

    private final SpringDataKnowledgePointRepository kpRepository;

    public KnowledgeGraphQueryServiceImpl(SpringDataKnowledgePointRepository kpRepository) {
        this.kpRepository = kpRepository;
    }

    @Override
    public KnowledgeGraphDto findGraph(String entity, int hops) {
        int h = Math.min(3, Math.max(1, hops));
        if (entity == null || entity.isBlank()) {
            return KnowledgeGraphDto.empty("", h);
        }
        List<KnowledgePointEntity> seeds = resolveSeeds(entity);
        if (seeds.isEmpty()) {
            return KnowledgeGraphDto.empty(entity, h);
        }

        Map<UUID, GraphNodeTmp> nodeMap = new LinkedHashMap<>();
        List<EdgeTmp> edges = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        for (KnowledgePointEntity seed : seeds) {
            nodeMap.put(seed.getId(), toNode(seed, "KNOWLEDGE_POINT"));
        }
        // 第 1 跳: 先修 / 相关
        List<UUID> frontier = new ArrayList<>(nodeMap.keySet());
        for (int level = 1; level <= h; level++) {
            List<UUID> next = new ArrayList<>();
            for (UUID id : frontier) {
                if (!visited.add(id)) {
                    continue;
                }
                kpRepository.findById(id).ifPresent(kp -> {
                    for (UUID pre : uuids(kp.getPrerequisites())) {
                        nodeMap.putIfAbsent(pre, null);
                        edges.add(new EdgeTmp(pre, id, "PREREQUISITE"));
                    }
                    for (UUID rel : uuids(kp.getRelatedPoints())) {
                        nodeMap.putIfAbsent(rel, null);
                        edges.add(new EdgeTmp(id, rel, "RELATES_TO"));
                    }
                });
            }
            // 补齐本层未解析节点
            List<UUID> unknown = nodeMap.entrySet().stream()
                    .filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey)
                    .limit(MAX_FANOUT)
                    .toList();
            List<KnowledgePointEntity> loaded = kpRepository.findByIds(unknown);
            for (KnowledgePointEntity e : loaded) {
                nodeMap.put(e.getId(), toNode(e, "KNOWLEDGE_POINT"));
            }
            next.addAll(nodeMap.keySet());
            frontier = next;
            if (nodeMap.size() > MAX_FANOUT + MAX_SEEDS) {
                break;
            }
        }
        log.debug("kg query entity={} hops={} nodes={} edges={}",
                entity, h, nodeMap.size(), edges.size());
        return new KnowledgeGraphDto(entity, h,
                nodeMap.values().stream().filter(n -> n != null)
                        .map(n -> new GraphNode(n.id.toString(), n.name, n.code, n.courseId,
                                n.difficulty, n.type)).toList(),
                edges.stream().map(e -> new GraphEdge(e.source.toString(), e.target.toString(),
                        e.relation)).toList());
    }

    private List<KnowledgePointEntity> resolveSeeds(String entity) {
        try {
            UUID id = UUID.fromString(entity);
            return kpRepository.findById(id).map(List::of).orElse(List.of());
        } catch (IllegalArgumentException notUuid) {
            return kpRepository.searchByEntity(entity, PageRequest.of(0, MAX_SEEDS));
        }
    }

    private static List<UUID> uuids(UUID[] arr) {
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    private static GraphNodeTmp toNode(KnowledgePointEntity e, String type) {
        return new GraphNodeTmp(e.getId(), e.getName(), e.getCode(),
                e.getCourseId() == null ? null : e.getCourseId().toString(),
                e.getDifficulty(), type);
    }

    private record GraphNodeTmp(UUID id, String name, String code, String courseId,
                                int difficulty, String type) {}

    private record EdgeTmp(UUID source, UUID target, String relation) {}
}
