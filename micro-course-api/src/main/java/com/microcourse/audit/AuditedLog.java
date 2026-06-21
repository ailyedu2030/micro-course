package com.microcourse.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（P2-10 + P3-12）。
 *
 * <p>在需要审计的 Controller 方法上标注，由 {@link AuditedLogInterceptor}
 * 在请求完成后异步写入 {@code operation_logs} 表。声明式、统一、零侵入业务代码。</p>
 *
 * <p>用法：</p>
 * <pre>
 * &#64;PostMapping("/login")
 * &#64;AuditedLog("用户登录")
 * public R&lt;LoginResponse&gt; login(...) { ... }
 * </pre>
 *
 * <p><b>实现说明：</b>运行期捕获由 {@link AuditedLogInterceptor}（Spring MVC
 * {@code HandlerInterceptor}）完成，而非 AspectJ {@code @Aspect}。原因与
 * {@code RequireRole} 一致：项目 classpath 仅含 {@code spring-aop}、不含
 * {@code aspectjweaver}，且约束"不引入新依赖"。{@code HandlerInterceptor} 来自
 * 既有 {@code spring-webmvc}，零新依赖、零代理开销，且与方法级审计场景天然契合。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditedLog {

    /**
     * 操作描述（写入 {@code operation_logs.action} 字段）。
     *
     * <p>可为人类可读的中文短语（如 {@code "用户登录"}）或动词_名词枚举
     * （如 {@code "USER_LOGIN"}），具体格式约定见 {@code docs/操作日志规范.md}。</p>
     */
    String value();
}
