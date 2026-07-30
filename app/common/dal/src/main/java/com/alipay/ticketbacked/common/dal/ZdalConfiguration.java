package com.alipay.ticketbacked.common.dal;

import com.alipay.zdal.client.jdbc.ZdalDataSource;
import com.alipay.zdal.client.jdbc.builder.ZdalDataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionManager;

import javax.sql.DataSource;

/**
 * ZDAL 数据源配置 — 编程式创建 ZdalDataSource
 * 参考官方 demo 项目规范，使用 ZdalDataSourceBuilder 创建数据源
 * 本地和 Linke 均走 ZDAL，本地需启动 meshboot 提供 DDS 配置服务
 */
@Configuration
public class ZdalConfiguration {

    /**
     * 单库单表数据源
     * 应用名称: zzvonehxtickettest (与 DDS 平台注册一致，spring.application.name 是 ticketbacked，不一致所以需显式声明)
     * 应用数据源名称: zzvonehxticket
     * 应用数据源版本: mesh (dbMesh 数据源)
     */
    @Bean(initMethod = "init")
    public ZdalDataSource dataSource() {
        return ZdalDataSourceBuilder.create()
                .appName("zzvonehxtickettest")
                .appDsName("zzvonehxticket")
                .version("mesh")
                .useDbMesh(false)
                .build();
    }

    /**
     * 事务管理器
     */
    @Bean
    public TransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}