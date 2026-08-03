package com.alipay.ticketbacked.common.dal;

import com.alipay.zdal.client.jdbc.ZdalDataSource;
import com.alipay.zdal.client.jdbc.builder.ZdalDataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

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
