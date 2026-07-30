package com.alipay.ticketbacked.common.dal;

import com.alipay.zdal.client.jdbc.ZdalDataSource;
import com.alipay.zdal.client.jdbc.builder.ZdalDataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ZDAL 数据源配置 — 通过 ZdalDataSourceBuilder 创建 DataSource Bean
 * DDS 控制台参数: appName=zzvonehxtickettest, appDsName=zzvonehxtickettest, version=v1.0.0, dbType=OB
 */
@Configuration
public class ZdalConfiguration {

    @Bean(initMethod = "init")
    public ZdalDataSource dataSource() {
        return ZdalDataSourceBuilder.create()
                .appName("zzvonehxtickettest")
                .appDsName("zzvonehxtickettest")
                .version("v1.0.0")
                .useDbMesh(false)
                .build();
    }
}