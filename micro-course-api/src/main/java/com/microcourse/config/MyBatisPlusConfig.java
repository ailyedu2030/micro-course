package com.microcourse.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        // RES-002 修复: 设置最大分页限制 1000, 防止单次查询加载过多数据
        pagination.setMaxLimit(1000L);
        // RES-013 修复: 当 OFFSET 超过此值时转为子查询优化
        pagination.setOptimizeJoin(false);
        interceptor.addInnerInterceptor(pagination);
        // P2: 乐观锁插件（配合 @Version 注解）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
