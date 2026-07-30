package com.alipay.ticketbacked;

import com.alipay.sofa.boot.reader.VelocityXmlBeanDefinitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizationAutoConfiguration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 排除 DataSourceAutoConfiguration:
 * DDS Starter 读取 com.alipay.sofa.dds.* 配置自动创建 ZdalDataSource
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionManagerCustomizationAutoConfiguration.class
})
@EnableScheduling
@ImportResource(locations = "classpath*:spring/*.xml", reader = VelocityXmlBeanDefinitionReader.class)
public class TicketbackedApplication {
	private static final Logger LOGGER = LoggerFactory.getLogger(TicketbackedApplication.class);

	public static void main(String[] args) {
		try {
			SpringApplication.run(TicketbackedApplication.class, args);
			LOGGER.info("SOFABoot Application Start!!!");
		} catch (Throwable e) {
			LOGGER.error("SOFABoot Application Start Fail!!! More logs can be found on 1) logs/sofa-runtime/common-error.log"
					+ " 2) logs/spring/spring.log 3) logs/mvc/common-error.log 4) logs/health-check/common-error.log", e);
			throw e;
		}
	}

}