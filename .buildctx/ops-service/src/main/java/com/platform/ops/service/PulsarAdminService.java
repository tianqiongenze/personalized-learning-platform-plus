package com.platform.ops.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Pulsar 消息巡检: 通过 Broker HTTP REST(/admin/v2) 查看
 * 集群/租户/命名空间/主题/订阅/积压(backlog), 并可 PEEK 查看具体消息内容。
 * 关键 REST(文档: /admin/v2/):
 *   GET  /admin/v2/tenants
 *   GET  /admin/v2/namespaces/{tenant}
 *   GET  /admin/v2/persistent/{tenant}/{ns}
 *   GET  /admin/v2/persistent/{tenant}/{ns}/{topic}/stats
 *   GET  /admin/v2/persistent/{tenant}/{ns}/{topic}/subscriptions
 *   GET  /admin/v2/persistent/{tenant}/{ns}/{topic}/subscription/{sub}/peekmessages?n=10
 */
@Service
public class PulsarAdminService {

    private final RestClient client;
    private final String base;

    public PulsarAdminService(@Value("${app.endpoints.pulsar}") String brokerHttp) {
        this.base = brokerHttp.replaceAll("/$", "");
        this.client = RestClient.builder().baseUrl(base).build();
    }

    public Object tenants() {
        return client.get().uri("/admin/v2/tenants").retrieve().body(Object.class);
    }

    public Object namespaces(String tenant) {
        return client.get().uri("/admin/v2/namespaces/{tenant}", tenant).retrieve().body(Object.class);
    }

    public Object topics(String tenant, String namespace) {
        return client.get().uri("/admin/v2/persistent/{tenant}/{ns}", tenant, namespace)
                .retrieve().body(Object.class);
    }

    @SuppressWarnings("unchecked")
    public Object topicStats(String tenant, String namespace, String topic) {
        return client.get().uri("/admin/v2/persistent/{tenant}/{ns}/{topic}/stats", tenant, namespace, topic)
                .retrieve().body(Object.class);
    }

    public Object subscriptions(String tenant, String namespace, String topic) {
        return client.get().uri("/admin/v2/persistent/{tenant}/{ns}/{topic}/subscriptions", tenant, namespace, topic)
                .retrieve().body(Object.class);
    }

    /** 查看具体消息内容(peek 不消费, 不影响订阅游标) */
    public Object peekMessages(String tenant, String namespace, String topic, String subscription, int count) {
        return client.get()
                .uri(uri -> uri.path("/admin/v2/persistent/{t}/{ns}/{topic}/subscription/{sub}/peekmessages")
                        .queryParam("count", count).build(tenant, namespace, topic, subscription))
                .retrieve().body(Object.class);
    }

    /** 平台主题总览: 消息速率/积压(聚合 stats) */
    public Object platformTopicsOverview() {
        return topics("public", "default");
    }
}
