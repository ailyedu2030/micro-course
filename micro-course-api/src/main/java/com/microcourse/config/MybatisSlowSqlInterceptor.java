package com.microcourse.config;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * MyBatis 慢 SQL 拦截器（P3-15）。
 *
 * <p>监控执行时间超过阈值的 SQL，记录到日志。覆盖选课、视频学习、答题等高频业务路径。
 *
 * <p>阈值与开关可通过 application.yml 配置：
 * <ul>
 *   <li>mybatis.slow-sql.threshold-ms（默认 500）</li>
 *   <li>mybatis.slow-sql.alert-enabled（默认 true）</li>
 * </ul>
 *
 * <p>UX 零退化设计要点：
 * <ol>
 *   <li>仅在 SQL 执行前后各取一次 System.currentTimeMillis()，开销 &lt; 1ms，对主流程零感知。</li>
 *   <li>所有监控/反射取 SQL 的逻辑包裹在独立 try-catch 内，任何监控自身异常都被吞掉并仅记录，
 *       绝不向上抛出污染业务执行结果（"合法用户操作零感 / 不阻塞主流程"硬约束）。</li>
 *   <li>本项目禁用 Lombok（见 pom.xml 注释），故手写 SLF4J Logger，不使用 {@code @Slf4j}。</li>
 * </ol>
 *
 * <p>通过 {@code @Component} 注册：MyBatis-Plus（基于 mybatis-spring-boot-starter）会自动收集容器内
 * 所有 {@link Interceptor} 类型的 bean 并注册到 SqlSessionFactory，无需手动 addInterceptor。
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query",
                args = {java.sql.Statement.class, org.apache.ibatis.session.ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update",
                args = {java.sql.Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch",
                args = {java.sql.Statement.class})
})
@Component
public class MybatisSlowSqlInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(MybatisSlowSqlInterceptor.class);

    private final long thresholdMs;
    private final boolean alertEnabled;

    public MybatisSlowSqlInterceptor(
            @Value("${mybatis.slow-sql.threshold-ms:500}") long thresholdMs,
            @Value("${mybatis.slow-sql.alert-enabled:true}") boolean alertEnabled) {
        this.thresholdMs = thresholdMs;
        this.alertEnabled = alertEnabled;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            // 真正执行业务 SQL —— 异常按原样向上抛出，监控逻辑绝不改变业务行为
            return invocation.proceed();
        } finally {
            // 监控逻辑完全隔离：任何异常都不得影响上面已返回/已抛出的业务结果
            long duration = System.currentTimeMillis() - startTime;
            if (duration >= thresholdMs && alertEnabled) {
                logSlowSql(invocation, duration);
            }
        }
    }

    /**
     * 记录慢 SQL。全程 try-catch 兜底：反射取 SQL/参数失败时仅降级记录，绝不抛出。
     */
    private void logSlowSql(Invocation invocation, long duration) {
        try {
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
            Object rawSql = metaObject.getValue("delegate.boundSql.sql");
            String sql = rawSql == null ? "<unknown>" : rawSql.toString().replaceAll("\\s+", " ").trim();
            Object parameterObject = statementHandler.getBoundSql().getParameterObject();
            log.warn("[SLOW SQL] duration={}ms (threshold={}ms), sql={}, params={}",
                    duration, thresholdMs, sql, parameterObject);
        } catch (Exception e) {
            // 监控自身异常不得阻塞主流程，仅降级提示
            log.warn("[SLOW SQL] duration={}ms (threshold={}ms), but failed to resolve SQL detail: {}",
                    duration, thresholdMs, e.getMessage());
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 阈值/开关已通过 @Value 注入，无需在此读取 properties
    }
}
