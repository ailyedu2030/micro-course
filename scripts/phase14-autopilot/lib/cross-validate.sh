#!/bin/bash
# =====================================================
# 4 维交叉验证 · 任务书生成器
# 用法: cross-validate.sh <dim> <batch_id>
#   dim ∈ r1 | r2 | r3 | r4
#   batch_id 如 batch-1 / M1-batch-1
# =====================================================

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROGRESS_FILE="$PROJECT_ROOT/.audit-cache/phase14/progress.json"

dim="$1"
batch_id="$2"
[ -z "$dim" ] && { echo "用法: cross-validate.sh <r1|r2|r3|r4> <batch-id>"; exit 1; }
[ -z "$batch_id" ] && { echo "用法: cross-validate.sh <r1|r2|r3|r4> <batch-id>"; exit 1; }

# 找该 batch 的所有 ticket_id
tickets=$(jq -r ".batches[\"$batch_id\"].tickets[]?" "$PROGRESS_FILE" 2>/dev/null)
[ -z "$tickets" ] && { echo "❌ batch 不存在: $batch_id"; exit 1; }

tickets_json=$(echo "$tickets" | jq -R . | jq -s .)

case "$dim" in
  r1)
    # R1 · 代码质量+契约
    cat <<EOF
{
  "reviewer_id": "R1-$(date +%s)",
  "dim": "R1 · 代码质量+契约",
  "batch_id": "$batch_id",
  "ticket_ids": $tickets_json,
  "review_scope": [
    "Lombok 残留（@Data/@Builder/@Slf4j 误用）",
    "@Autowired 字段注入（应构造器注入）",
    "分页 5 字段（total/page/size/totalPages/list）",
    "响应格式（R<T> 统一）",
    "Controller @PreAuthorize 与权限矩阵对齐",
    "ErrorCode 使用一致性（不用裸 RuntimeException）",
    "Service @Transactional + rollbackFor",
    "Entity 与 DB 字段对齐（camelCase / snake_case）"
  ],
  "evidence_needed": [
    "每条问题带 file_path:line_number",
    "P0/P1/P2/P3 分级",
    "修复建议（最小可行）"
  ],
  "deliverable": "JSON 报告",
  "boundary": "❌ 禁止改代码，只读 + 报告"
}
EOF
    ;;
  r2)
    cat <<EOF
{
  "reviewer_id": "R2-$(date +%s)",
  "dim": "R2 · DB 迁移 vs 数据字典",
  "batch_id": "$batch_id",
  "ticket_ids": $tickets_json,
  "review_scope": [
    "新加/修改的 Flyway migration 与 docs/数据字典.md 是否一致",
    "字段类型/长度/默认值是否对齐",
    "FK 链是否完整（ON DELETE 策略）",
    "索引是否覆盖高频查询",
    "唯一约束是否覆盖业务关键字段",
    "PostgreSQL 17 特性使用是否正确"
  ],
  "evidence_needed": [
    "每条问题带 migration 文件:行号 + 数据字典.md:行号",
    "P0/P1/P2/P3 分级"
  ],
  "deliverable": "JSON 报告",
  "boundary": "❌ 禁止改代码，只读 + 报告"
}
EOF
    ;;
  r3)
    cat <<EOF
{
  "reviewer_id": "R3-$(date +%s)",
  "dim": "R3 · 安全 + 配置",
  "batch_id": "$batch_id",
  "ticket_ids": $tickets_json,
  "review_scope": [
    "pom.xml / package.json 依赖 CVE 风险",
    "application.yml 密钥/数据库密码/Sentry DSN",
    "Redis key 格式（mc: 前缀 + 业务域）",
    "JWT claims 完整性（iat/exp/sub/role）",
    "SecurityConfig 路径白名单 vs 黑名单",
    "CORS 配置",
    "SQL 注入（MyBatis-Plus 参数化 vs 拼接）",
    "XSS 过滤",
    "CSRF 防护"
  ],
  "evidence_needed": [
    "每条问题带 file_path:line_number",
    "P0/P1/P2/P3 分级"
  ],
  "deliverable": "JSON 报告",
  "boundary": "❌ 禁止改代码，只读 + 报告"
}
EOF
    ;;
  r4)
    cat <<EOF
{
  "reviewer_id": "R4-$(date +%s)",
  "dim": "R4 · 跨域一致性",
  "batch_id": "$batch_id",
  "ticket_ids": $tickets_json,
  "review_scope": [
    "FK 关系链（Entity 字段 vs 实际 JOIN）",
    "Entity 命名 vs SQL 表名",
    "Controller REST 路径 vs 权限矩阵",
    "Service 接口 vs Controller 调用",
    "前端 API 封装 vs 后端 Controller 路径",
    "前端 Pinia store vs 后端响应字段",
    "状态机 from→to 与 spec §2 对齐"
  ],
  "evidence_needed": [
    "每条问题带 file_path:line_number + spec 章节",
    "P0/P1/P2/P3 分级"
  ],
  "deliverable": "JSON 报告",
  "boundary": "❌ 禁止改代码，只读 + 报告"
}
EOF
    ;;
  *)
    echo "未知 dim: $dim（仅 r1/r2/r3/r4）"
    exit 1
    ;;
esac
