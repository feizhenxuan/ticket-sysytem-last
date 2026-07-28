# 使用说明

## 快速开始
应用的启动类`com.alipay.ticketbacked.TicketbackedApplication`位于`bootstrap`模块的`src/main/java`目录下，本地开发时直接运行该类的`main`方法即可启动应用

应用的集成测试基类`com.alipay.ticketbacked.AbstractTestBase`位于`bootstrap`或`test`模块的`src/test/java`目录下，继承该类的单元测试类可以集成SOFABoot应用启动

应用采用 [半关闭模块化](https://yuque.antfin-inc.com/middleware/sofaboot/modular#%E5%8D%8A%E5%85%B3%E9%97%AD%E6%A8%A1%E5%9D%97%E5%8C%96) 模式，应用内各模块之间没有上下文隔离，不需要手动添加 MANIFEST.MF 文件

## SOFABoot 用户文档
你可以通过下述文档了解到 SOFABoot 的使用细节
+ SOFABoot 应用开发: [主站版SOFABoot应用开发概述](https://yuque.antfin-inc.com/middleware/sofaboot/db7fgl)
+ SOFABoot 版本: [主站版SOFABoot发布报告](https://yuque.antfin-inc.com/middleware/sofaboot/releasenote)
+ SOFABoot 技术支持: [主站版SOFABoot常见问题](https://yuque.antfin-inc.com/middleware/sofaboot/faq)

## 组件使用示例
下面是各个组件的使用示例代码,您可以找到它们,使用这些示例代码前请阅读类上的注释说明
* SOFABoot Web 示例代码:
    + `JsonSampleController`: RESTful 服务示例代码
    + `VelocitySampleController`: Velocity 模版渲染示例代码

## 组件用户文档
下面是各个组件件的用户文档,您可以查看详细的使用说明、工作原理:

* [SOFABoot Web Starter 用户文档](https://yuque.antfin-inc.com/middleware/sofaboot/rml0bg)
* [Tracer 用户文档](https://yuque.antfin-inc.com/middleware/tracer)

