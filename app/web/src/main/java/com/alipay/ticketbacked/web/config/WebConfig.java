package com.alipay.ticketbacked.web.config;

import com.alipay.ticketbacked.web.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置 — CORS 跨域 + JWT 拦截器注册
 * CORS 由 CorsFilter 处理，确保在拦截器之前执行
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/health",
                        "/api/pay/notify",   // 支付宝回调不需要 JWT
                        "/api/pay/verify"     // 同步跳转验签
                );
    }
}