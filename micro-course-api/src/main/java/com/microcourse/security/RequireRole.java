package com.microcourse.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解（P3-6 权限注解常量化）。
 *
 * <p><b>叠加使用，不替换 {@code @PreAuthorize}。</b>本注解用于消除散落的 SpEL
 * 字符串（如 {@code hasAnyRole('TEACHER','ADMIN')} 在 Controller 中重复 20+ 次），
 * 把允许的角色以类型化数组集中表达，降低维护成本与漏写风险。</p>
 *
 * <p>用法（建议与现有 {@code @PreAuthorize} 叠加，渐进迁移）：</p>
 * <pre>
 * &#64;PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
 * &#64;RequireRole({"ADMIN", "ACADEMIC"})
 * public R&lt;Void&gt; updateStatus(...) { ... }
 * </pre>
 *
 * <p><b>实现说明：</b>本注解为纯标记注解，运行期校验由
 * {@link RequireRoleInterceptor}（Spring MVC {@code HandlerInterceptor}）执行，
 * 逻辑等价于 {@code hasAnyRole(value...)}。</p>
 *
 * <p>选用 {@code HandlerInterceptor} 而非 AspectJ {@code @Aspect} 的原因：项目
 * 当前 classpath 仅含 {@code spring-aop}、不含 {@code aspectjweaver}，且本阶段约束
 * "不引入新依赖"。{@code HandlerInterceptor} 来自既有的 {@code spring-webmvc}，
 * 零新依赖、零代理开销，且与本场景（Controller 方法级权限）天然契合。</p>
 *
 * <p><b>注意：</b>本注解不携带 {@code @PreAuthorize} 元注解。当前 Spring Security
 * 6.2.x 不支持注解模板表达式（{@code hasAnyRole({roles})} 占位符展开，该能力 6.3+
 * 才稳定），故角色校验改由拦截器在运行期完成，避免与方法级安全解析冲突。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 允许访问的角色列表（不含 {@code ROLE_} 前缀，如 {@code "TEACHER"}、{@code "ADMIN"}）。
     *
     * <p>语义为"任一匹配即放行"，等价于 {@code hasAnyRole(value...)}。</p>
     */
    String[] value();
}
