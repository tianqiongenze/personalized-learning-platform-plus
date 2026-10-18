package com.platform.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 课程资源服务主启动类
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.platform.course.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.platform.course.infrastructure.persistence.entity")
@ComponentScan(basePackages = "com.platform.course")
@EnableAsync
public class CourseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseServiceApplication.class, args);
    }
}