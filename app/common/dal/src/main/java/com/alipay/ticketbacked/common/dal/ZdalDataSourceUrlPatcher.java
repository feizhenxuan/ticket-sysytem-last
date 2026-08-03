package com.alipay.ticketbacked.common.dal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.zdal.patch-url", havingValue = "true")
public class ZdalDataSourceUrlPatcher implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ZdalDataSourceUrlPatcher.class);
    private static final String OLD_HOST = "127.0.0.1:2883";
    private static final String NEW_HOST = "zzvonehxtickettest-4.gz00b.dev.alipay.net:2883";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource) {
            String className = bean.getClass().getName();
            if (className.contains("ZdalDataSource") || className.contains("InternalZdalDataSource")) {
                log.info("[ZdalUrlPatcher] 检测到 ZdalDataSource，开始修改连接地址 {} -> {}", OLD_HOST, NEW_HOST);
                patchDataSource(bean);
            }
        }
        return bean;
    }

    @SuppressWarnings("unchecked")
    private void patchDataSource(Object zdalDataSource) {
        try {
            Map<String, DataSource> physicalDsMap = getFieldValue(zdalDataSource, "physicalDsIdToDataSourceMap");
            if (physicalDsMap == null) { log.warn("[ZdalUrlPatcher] 未找到 physicalDsIdToDataSourceMap"); return; }
            log.info("[ZdalUrlPatcher] 发现 {} 个物理数据源", physicalDsMap.size());
            for (Map.Entry<String, DataSource> entry : physicalDsMap.entrySet()) {
                patchPhysicalDataSource(entry.getValue());
            }
        } catch (Exception e) { log.error("[ZdalUrlPatcher] 修改失败", e); }
    }

    private void patchPhysicalDataSource(DataSource ds) {
        try {
            Object localTxDataSource = getFieldValue(ds, "deputyLocalTxDataSource");
            if (localTxDataSource == null) localTxDataSource = getFieldValue(ds, "localTxDataSource");
            if (localTxDataSource == null) { log.warn("[ZdalUrlPatcher] 未找到 LocalTxDataSource"); return; }
            Object connectionFactory = getFieldValue(localTxDataSource, "connectionFactory");
            if (connectionFactory == null) { log.warn("[ZdalUrlPatcher] 未找到 connectionFactory"); return; }
            String currentUrl = getFieldValue(connectionFactory, "connectionURL");
            if (currentUrl != null && currentUrl.contains(OLD_HOST)) {
                String newUrl = currentUrl.replace(OLD_HOST, NEW_HOST);
                try {
                    Method setter = connectionFactory.getClass().getMethod("setConnectionURL", String.class);
                    setter.invoke(connectionFactory, newUrl);
                    log.info("[ZdalUrlPatcher] setConnectionURL: {} -> {}", currentUrl, newUrl);
                } catch (NoSuchMethodException e) {
                    setFieldValue(connectionFactory, "connectionURL", newUrl);
                    log.info("[ZdalUrlPatcher] 字段修改 connectionURL: {} -> {}", currentUrl, newUrl);
                }
            }
        } catch (Exception e) { log.error("[ZdalUrlPatcher] 修改物理数据源失败", e); }
    }

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try { Field f = clazz.getDeclaredField(fieldName); f.setAccessible(true); return (T) f.get(obj); }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            catch (IllegalAccessException e) { return null; }
        }
        return null;
    }

    private void setFieldValue(Object obj, String fieldName, Object value) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try { Field f = clazz.getDeclaredField(fieldName); f.setAccessible(true); f.set(obj, value); return; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            catch (IllegalAccessException e) { return; }
        }
    }
}
