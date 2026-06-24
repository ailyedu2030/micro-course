#!/bin/bash
# =============================================================================
# DB 备份+恢复脚本 (条件 8)
# =============================================================================
# 部署要求: RPO < 1h, RTO < 4h
# 用法:
#   bash scripts/db-backup.sh                # 备份到 backups/db/
#   bash scripts/db-backup.sh --restore FILE  # 从备份恢复
# =============================================================================

set -e

DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-micro_course}
DB_USER=${DB_USER:-postgres}

BACKUP_DIR="backups/db"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"

# 恢复模式
if [ "$1" == "--restore" ]; then
  RESTORE_FILE="$2"
  if [ -z "$RESTORE_FILE" ]; then
    echo "用法: $0 --restore <backup-file>"
    echo "可用备份:"
    ls -lh "$BACKUP_DIR"/*.sql.gz 2>/dev/null | tail -10
    exit 1
  fi
  if [ ! -f "$RESTORE_FILE" ]; then
    echo "❌ 备份文件不存在: $RESTORE_FILE"
    exit 1
  fi

  echo "⚠️  警告: 将覆盖当前数据库 $DB_NAME"
  read -p "确认恢复? 输入 YES 继续: " confirm
  if [ "$confirm" != "YES" ]; then
    echo "取消恢复"
    exit 0
  fi

  echo "恢复中: $RESTORE_FILE"
  gunzip -c "$RESTORE_FILE" | PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" --single-transaction
  echo "✓ 恢复完成"
  exit 0
fi

# 备份模式
mkdir -p "$BACKUP_DIR"

# 清理 30 天前的备份
echo "[0/3] 清理 30 天前的旧备份..."
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +30 -delete 2>/dev/null
OLD_COUNT=$(find "$BACKUP_DIR" -name "*.sql.gz" 2>/dev/null | wc -l)
echo "      保留备份数: $OLD_COUNT"

# 备份
echo "[1/3] 正在备份 $DB_NAME..."
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  --format=plain --no-owner --no-privileges \
  | gzip > "$BACKUP_FILE"

if [ ! -s "$BACKUP_FILE" ]; then
  echo "❌ 备份失败: $BACKUP_FILE 为空"
  exit 1
fi

BACKUP_SIZE=$(ls -lh "$BACKUP_FILE" | awk '{print $5}')
echo "      备份文件: $BACKUP_FILE ($BACKUP_SIZE)"

# 校验备份
echo "[2/3] 校验备份完整性..."
if gunzip -t "$BACKUP_FILE" 2>/dev/null; then
  echo "      ✓ gzip 完整性 OK"
else
  echo "      ❌ gzip 完整性失败!"
  exit 1
fi

# 输出清单
echo "[3/3] 当前备份清单:"
ls -lh "$BACKUP_DIR"/*.sql.gz 2>/dev/null | tail -5

echo ""
echo "=== 备份完成 ==="
echo "文件: $BACKUP_FILE"
echo ""
echo "建议:"
echo "  - 每日 cron 跑: 0 * * * * bash $0  # 每小时备份"
echo "  - 上传 OSS/S3: aws s3 cp $BACKUP_FILE s3://backups/db/"
echo "  - 恢复测试: bash $0 --restore $BACKUP_FILE"
