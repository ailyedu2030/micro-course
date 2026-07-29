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

import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 敏感参数 key 列表（大小写不敏感匹配），包含时对对应值进行脱敏 */
    private static final java.util.List<String> SENSITIVE_PARAM_KEYS = Arrays.asList(
            "password", "pwd", "phone", "mobile", "email", "id_card", "idcard",
            "real_name", "realname", "id_card_back", "id_card_front", "bank_card",
            "bankcard", "credit_card", "credentials", "token", "refresh_token",
            "access_token", "secret", "api_key", "apikey"
    );

    /** 脱敏正则：长度 &ge; 8 显示首尾各 2 位中间 ***，&lt; 8 显示首 1 位 *** 尾 1 位 */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)(" + String.join("|", SENSITIVE_PARAM_KEYS) + ")\\s*=\\s*([^,&}\\s]+)"
    );

    /**
     * 记录慢 SQL（P1C-8：日志参数脱敏）。全程 try-catch 兜底：反射取 SQL/参数失败时仅降级记录，绝不抛出。
     */
    private void logSlowSql(Invocation invocation, long duration) {
        try {
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
            Object rawSql = metaObject.getValue("delegate.boundSql.sql");
            String sql = rawSql == null ? "<unknown>" : rawSql.toString().replaceAll("\\s+", " ").trim();
            Object parameterObject = statementHandler.getBoundSql().getParameterObject();
            String paramsStr = parameterObject == null ? "null" : maskSensitiveParams(parameterObject.toString());
            log.warn("[SLOW SQL] duration={}ms (threshold={}ms), sql={}, params={}",
                    duration, thresholdMs, sql, paramsStr);
        } catch (Exception e) {
            // 监控自身异常不得阻塞主流程，仅降级提示
            log.warn("[SLOW SQL] duration={}ms (threshold={}ms), but failed to resolve SQL detail: {}",
                    duration, thresholdMs, e.getMessage());
        }
    }

    /**
     * 对参数字符串中的敏感字段值进行脱敏处理。
     * <p>匹配格式：key=value（键值对），若 key 属于敏感字段则对 value 做掩码处理：
     * <ul>
     *   <li>value 长度 &ge; 8：显示首 2 尾 2，中间替换为 ****</li>
     *   <li>value 长度 &lt; 8：显示首 1 尾 1，中间替换为 ****</li>
     * </ul>
     * 非敏感字段原样保留。
     */
    private String maskSensitiveParams(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuffer sb = new StringBuffer(input.length());
        Matcher matcher = SENSITIVE_FIELD_PATTERN.matcher(input);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            String masked;
            if (value.length() >= 8) {
                masked = value.substring(0, 2) + "****" + value.substring(value.length() - 2);
            } else if (value.length() >= 2) {
                masked = value.substring(0, 1) + "****" + value.substring(value.length() - 1);
            } else {
                masked = "****";
            }
            matcher.appendReplacement(sb, key + "=" + masked);
        }
        matcher.appendTail(sb);
        return sb.toString();
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
