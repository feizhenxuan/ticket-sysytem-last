package com.alipay.ticketbacked;

import com.alipay.sofa.boot.reader.VelocityXmlBeanDefinitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 不排除 DataSourceAutoConfiguration：
 * DDS Starter (dds-alipay-sofa-boot-starter) 会读取 com.alipay.sofa.dds.* 配置，
 * 自动创建 ZdalDataSource 并以 @Primary 覆盖默认数据源
 */
@SpringBootApplication(exclude = { HibernateJpaAutoConfiguration.class })
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