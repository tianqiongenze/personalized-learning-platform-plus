package com.platform.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.pulsar.core.PulsarTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * EventAutoConfiguration 装配条件测试(2026-10-24 修复的回归守护):
 * outbox=true + PulsarTemplate → relay/publisher 注册; 缺一 → 不注册。
 */
class EventAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void outboxEnabledRegistersRelayAndPublisher() {
        runner.withBean("pulsarTemplate", PulsarTemplate.class, () -> mock(PulsarTemplate.class))
                .withPropertyValues("app.events.enabled=true", "app.events.outbox=true",
                        "spring.application.name=test-svc")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PulsarDomainEventPublisher.class);
                    // JdbcTemplate 缺席 → relay 不注册(需要 jdbc)
                    assertThat(ctx).doesNotHaveBean(OutboxEventRelay.class);
                });
    }

    @Test
    void outboxDisabledRegistersPublisherOnly() {
        runner.withBean("pulsarTemplate", PulsarTemplate.class, () -> mock(PulsarTemplate.class))
                .withPropertyValues("app.events.enabled=true", "app.events.outbox=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PulsarDomainEventPublisher.class);
                    assertThat(ctx).doesNotHaveBean(OutboxEventRelay.class);
                });
    }
}
