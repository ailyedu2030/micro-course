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
        // RES-002 修复 + P1-I-2026-08-15: 设置最大分页限制 ApiLimits.MAX_PAGE_SIZE(100)
        // （R3 审查确认"双层防御"：Controller @Range 放行 10000 兼容前端，此处全局拦截到 100 = 真正的第二层防线）
        pagination.setMaxLimit((long) com.microcourse.constants.ApiLimits.MAX_PAGE_SIZE);
        // RES-013 修复: 当 OFFSET 超过此值时转为子查询优化
        pagination.setOptimizeJoin(false);
        interceptor.addInnerInterceptor(pagination);
        // P2: 乐观锁插件（配合 @Version 注解）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
