package com.platform.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一运维监测平台后端
 * 聚合: Kubernetes(部署/Pod/HPA/事件/Pod日志) + Prometheus(主机/JVM/熔断限流指标)
 *      + Grafana(HTTP API/嵌入) + Pulsar(主题/订阅/积压/消息查看)
 *      + Redis Cluster(实时INFO/keyspace/slowlog) + Milvus(集合统计) + Nebula(状态) + Loki(日志)
 */
@SpringBootApplication
public class OpsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsServiceApplication.class, args);
    }
}
