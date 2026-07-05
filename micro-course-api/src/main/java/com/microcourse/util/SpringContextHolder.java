package com.microcourse.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文持有者，用于在非 Bean 管理类中获取 Bean 实例
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(SpringContextHolder.class);

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContextHolder.applicationContext = applicationContext;
    }

    /**
     * 根据类型获取 Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            log.warn("获取Bean失败: {}", e.getMessage());
            return null;
        }
    }
}
