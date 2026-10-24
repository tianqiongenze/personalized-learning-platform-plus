package com.platform.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.pulsar.core.PulsarTemplate;

/**
 * 事件装配(2026-10-24 修复)。
 *
 * 根因: PulsarDomainEventPublisher/OutboxEventRelay 是普通 @Component, 但平台各服务
 * @SpringBootApplication 默认只扫本服务包(com.platform.<svc>), com.platform.common.event
 * 永远不在扫描范围 → 这两个 bean 从未在任何服务注册(配而未接, 隐蔽 6 天)。
 * 修复: 经 AutoConfiguration.imports 注册(与 cache/tenant/web 等 auto-config 同机制)。
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.autoconfigure.pulsar.PulsarAutoConfiguration"})
@ConditionalOnClass(PulsarTemplate.class)
@ConditionalOnProperty(prefix = "app.events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EventAutoConfiguration {

    @Bean
    @ConditionalOnBean(PulsarTemplate.class)
    public PulsarDomainEventPublisher pulsarDomainEventPublisher(PulsarTemplate<String> pulsarTemplate,
                                                                 ObjectMapper objectMapper,
                                                                 org.springframework.core.env.Environment env) {
        return new PulsarDomainEventPublisher(pulsarTemplate, objectMapper, env);
    }

    @Bean
    @ConditionalOnBean({PulsarTemplate.class, JdbcTemplate.class})
    @ConditionalOnProperty(prefix = "app.events", name = "outbox", havingValue = "true")
    public OutboxEventRelay outboxEventRelay(ObjectProvider<JdbcTemplate> jdbc,
                                             PulsarDomainEventPublisher publisher,
                                             ObjectMapper objectMapper,
                                             org.springframework.core.env.Environment env) {
        return new OutboxEventRelay(jdbc, publisher, objectMapper, env);
    }
}
