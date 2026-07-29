package com.alipay.ticketbacked.common.dal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置 — 扫描 Mapper 接口
 * DataSource 由 DDS 自动注入（联调环境）或 Spring Boot 自动装配（本地 HikariCP）
 * SqlSessionFactory 由 mybatis-spring-boot-starter 自动配置，读取 application.properties 中的
 * mybatis.mapper-locations / mybatis.type-aliases-package / mybatis.configuration.map-underscore-to-camel-case
 */
@Configuration
@MapperScan("com.alipay.ticketbacked.common.dal.mapper")
public class MyBatisConfig {
}