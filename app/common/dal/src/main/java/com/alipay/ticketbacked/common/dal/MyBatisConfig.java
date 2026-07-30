package com.alipay.ticketbacked.common.dal;

import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis 配置 — 参考官方 demo 规范
 * 使用 @Qualifier 绑定 ZdalConfiguration 中创建的 dataSource
 * 通过 @MapperScan + sqlSessionFactoryRef 绑定 Mapper
 */
@Configuration
@MapperScan(basePackages = "com.alipay.ticketbacked.common.dal.mapper",
            sqlSessionFactoryRef = "sqlSessionFactoryBean")
public class MyBatisConfig {

    @Bean
    public SqlSessionFactoryBean sqlSessionFactoryBean(@Qualifier("dataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/mapper/*.xml"));
        factory.setTypeAliasesPackage("com.alipay.ticketbacked.core.model");
        return factory;
    }
}