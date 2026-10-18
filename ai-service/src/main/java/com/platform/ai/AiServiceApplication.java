package com.platform.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 服务: Spring AI(OpenAI 兼容端点) + Milvus RAG + 知识图谱 + 6 类资源生成
 * @EnableScheduling: 驱动 OutboxEventRelay 定时投递(app.events.outbox=true 时生效)
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
