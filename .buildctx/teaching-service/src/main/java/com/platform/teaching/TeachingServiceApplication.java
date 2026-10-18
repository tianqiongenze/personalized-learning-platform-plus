package com.platform.teaching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 教学互动服务主启动类
 * 提供学习路径、作业、考试、讨论、直播等教学互动功能
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.platform.teaching.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.platform.teaching.infrastructure.persistence.entity")
@ComponentScan(basePackages = "com.platform.teaching")
@EnableAsync
public class TeachingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeachingServiceApplication.class, args);
    }
}