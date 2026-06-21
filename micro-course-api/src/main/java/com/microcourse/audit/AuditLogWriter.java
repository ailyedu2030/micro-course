package com.microcourse.audit;

import com.microcourse.entity.OperationLog;
import com.microcourse.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志异步写入器（P2-10）。
 *
 * <p><b>为何独立成 bean：</b>{@code @Async} 仅在通过 Spring 代理跨 bean 调用时生效。
 * 若在拦截器内部 self-invocation 一个本类的 {@code @Async} 方法，代理被绕过，
 * 写入将退化为同步执行、阻塞主流程。故将异步写入逻辑下沉到本独立 {@code @Component}，
 * 由 {@link AuditedLogInterceptor} 注入并调用，确保 {@code @Async} 真正异步生效。</p>
 *
 * <p><b>UX 零退化保证：</b></p>
 * <ul>
 *   <li>异步：使用 {@code AsyncConfig} 定义的有界线程池 {@code taskExecutor}，不占用请求线程。</li>
 *   <li>不阻塞：调用方 fire-and-forget，不等待写入结果。</li>
 *   <li>失败隔离：写入异常在本方法内 try-catch 吞掉并仅记日志，绝不抛回主流程。</li>
 * </ul>
 */
@Component
public class AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogWriter.class);

    private final OperationLogService operationLogService;

    public AuditLogWriter(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 异步写入一条操作日志。任何异常均被吞掉，不影响主流程。
     *
     * @param entry 已构造完成的操作日志实体（含 userId/action 等，由调用方在请求线程内填好）
     */
    @Async("taskExecutor")
    public void write(OperationLog entry) {
        try {
            operationLogService.log(entry);
        } catch (Exception e) {
            log.warn("[AuditLog] 操作日志写入失败（不影响主流程）: action={}, target={}",
                    entry.getAction(), entry.getTargetType(), e);
        }
    }
}
