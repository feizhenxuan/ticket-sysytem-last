package com.alipay.ticketbacked.common.dal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置 — 仅扫描 Mapper 接口
 * SqlSessionFactory 由 mybatis-spring-boot-starter 自动配置
 * DataSource 由 DDS Starter (dds-alipay-sofa-boot-starter) 自动注入
 */
@Configuration
@MapperScan("com.alipay.ticketbacked.common.dal.mapper")
public class MyBatisConfig {
}