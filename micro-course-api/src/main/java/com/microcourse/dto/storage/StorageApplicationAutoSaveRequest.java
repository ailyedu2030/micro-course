package com.microcourse.dto.storage;

/**
 * 微专业申请表 — 自动保存请求 DTO。
 *
 * <p>与 {@link StorageApplicationSaveRequest} 字段结构相同，但<b>移除所有校验注解</b>
 * （{@code @NotBlank}、{@code @Min}、{@code @Pattern} 等），
 * 因为自动保存允许发送不完整的表单数据。
 * 完整校验在 submit() 时由 {@link StorageApplicationSaveRequest} + {@code @Valid} 执行。</p>
 *
 * <p>本类继承自 {@link StorageApplicationSaveRequest}，自动获得全部字段。
 * 仅在自动保存场景使用：{@code @RequestBody StorageApplicationAutoSaveRequest}，
 * 配合 {@code @Valid} 注解不调用父类校验（因父类继承的校验注解会生效），
 * 所以 Controller 方法上<b>不使用</b> {@code @Valid}。</p>
 */
public class StorageApplicationAutoSaveRequest extends StorageApplicationSaveRequest {

    /** 客户端心跳时间戳（保活/校时用，可选） */
    private Long heartbeatTimestamp;

    public StorageApplicationAutoSaveRequest() {
        super();
    }

    public Long getHeartbeatTimestamp() { return heartbeatTimestamp; }
    public void setHeartbeatTimestamp(Long heartbeatTimestamp) { this.heartbeatTimestamp = heartbeatTimestamp; }
}