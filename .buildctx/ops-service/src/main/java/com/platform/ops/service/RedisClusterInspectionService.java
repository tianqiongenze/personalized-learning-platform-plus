package com.platform.ops.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 多级缓存实时巡检:
 *  - L1 Caffeine: 指标经各服务 Micrometer 暴露, 由 PrometheusService 查询(caffeine_* 指标)
 *  - L2 Redis Cluster: 直连集群逐节点 INFO/keyspace/slowlog
 * 踩坑: Cluster 模式下要遍历每个 master/replica 节点分别 INFO, 不能只查入口节点
 */
@Service
public class RedisClusterInspectionService {

    private final RedisConnectionFactory connectionFactory;

    @Value("${spring.data.redis.cluster.nodes:}")
    private String nodesConfig;

    public RedisClusterInspectionService(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public List<java.util.Map<String, Object>> clusterInspection() {
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        if (!(connectionFactory.getConnection() instanceof RedisClusterConnection cluster)) {
            // 单机模式(本地开发): 直接 INFO
            Properties info = connectionFactory.getConnection().info();
            result.add(nodeInfo("standalone", info));
            return result;
        }
        cluster.clusterGetNodes().forEach(node -> {
            try {
                Properties info = cluster.info(node, "memory,stats,keyspace,clients,cpu");
                result.add(nodeInfo(node.getId() + "@" + node.getHost() + ":" + node.getPort(), info));
            } catch (Exception e) {
                result.add(java.util.Map.of(
                        "node", node.getId(),
                        "reachable", false,
                        "error", String.valueOf(e.getMessage())));
            }
        });
        return result;
    }

    private java.util.Map<String, Object> nodeInfo(String nodeId, Properties info) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("reachable", true);
        m.put("node", nodeId);
        m.put("usedMemoryHuman", info.getProperty("used_memory_human"));
        m.put("maxMemoryHuman", info.getProperty("maxmemory_human", "0"));
        m.put("connectedClients", info.getProperty("connected_clients"));
        m.put("instantaneousOpsPerSec", info.getProperty("instantaneous_ops_per_sec"));
        m.put("totalConnectionsReceived", info.getProperty("total_connections_received"));
        m.put("keyspaceHits", info.getProperty("keyspace_hits"));
        m.put("keyspaceMisses", info.getProperty("keyspace_misses"));
        m.put("evictedKeys", info.getProperty("evicted_keys"));
        m.put("expiredKeys", info.getProperty("expired_keys"));
        long hits = parse(info.getProperty("keyspace_hits"));
        long misses = parse(info.getProperty("keyspace_misses"));
        m.put("hitRatio", (hits + misses) == 0 ? 0 : Math.round(hits * 1000.0 / (hits + misses)) / 1000.0);
        m.put("role", info.getProperty("role"));
        return m;
    }

    private long parse(String v) {
        try {
            return v == null ? 0 : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 慢查询日志(管理操作入口) */
    public List<String> slowlog(int count) {
        try (var conn = connectionFactory.getConnection()) {
            var results = conn.execute("SLOWLOG", "GET".getBytes(),
                    String.valueOf(count).getBytes());
            return List.of(String.valueOf(results));
        }
    }

    /** 在线动态配置(管理操作): maxmemory-policy 等 */
    public String configSet(String param, String value) {
        try (var conn = connectionFactory.getConnection()) {
            conn.execute("CONFIG", "SET".getBytes(),
                    param.getBytes(), value.getBytes());
            return "OK";
        }
    }
}
