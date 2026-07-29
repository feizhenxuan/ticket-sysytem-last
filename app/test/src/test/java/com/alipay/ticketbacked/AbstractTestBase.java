package com.alipay.ticketbacked;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//注意本用例使用 Junit5 框架，请不要使用 Junit4 相关 API。继承该类的单元测试可以集成应用启动
@SpringBootTest
public abstract class AbstractTestBase {

	@Test
	public void invokeJunit5Test() {
	}

}
