package com.microcourse.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public class BatchApproveRequest {

    @NotEmpty(message = "ID 列表不能为空")
    private List<Long> ids;

    /** OP-0309: 可选版本号映射，key=ID, value=version，用于乐观锁校验 */
    private Map<Long, Long> idVersionMap;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }

    public Map<Long, Long> getIdVersionMap() { return idVersionMap; }
    public void setIdVersionMap(Map<Long, Long> idVersionMap) { this.idVersionMap = idVersionMap; }
}
