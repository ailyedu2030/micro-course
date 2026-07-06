#!/bin/bash
# 数据字典反向生成脚本
# 从 Flyway migration SQL 提取 CREATE TABLE / ALTER TABLE / CREATE INDEX / CHECK CONSTRAINT
# 输出 docs/data-dictionary.generated.md, 与 docs/数据字典.md diff 对比

set -e

OUTPUT_FILE="docs/data-dictionary.generated.md"
SRC_DIR="micro-course-api/src/main/resources/db/migration"

echo "[DB Schema Gen] 扫描 $SRC_DIR ..."

# 清空输出文件
echo "# 数据字典 (从 Flyway migration SQL 自动生成)" > "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "> 生成时间: $(date '+%Y-%m-%d %H:%M:%S')" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# 提取所有 CREATE TABLE
echo "## CREATE TABLE" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
for f in $(find "$SRC_DIR" -name "V*__*.sql" | sort); do
    grep -E "^CREATE TABLE" "$f" | while read line; do
        echo "- $line (\`$f\`)" >> "$OUTPUT_FILE"
    done
done
echo "" >> "$OUTPUT_FILE"

# 提取所有 ALTER TABLE ADD COLUMN
echo "## ALTER TABLE ADD COLUMN" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
for f in $(find "$SRC_DIR" -name "V*__*.sql" | sort); do
    grep -E "^ALTER TABLE.*ADD COLUMN" "$f" | while read line; do
        echo "- $line (\`$f\`)" >> "$OUTPUT_FILE"
    done
done
echo "" >> "$OUTPUT_FILE"

# 提取所有 CREATE INDEX
echo "## CREATE INDEX" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
for f in $(find "$SRC_DIR" -name "V*__*.sql" | sort); do
    grep -E "^CREATE (UNIQUE )?INDEX" "$f" | while read line; do
        echo "- $line (\`$f\`)" >> "$OUTPUT_FILE"
    done
done
echo "" >> "$OUTPUT_FILE"

# 提取所有 CHECK CONSTRAINT
echo "## CHECK CONSTRAINT" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
for f in $(find "$SRC_DIR" -name "V*__*.sql" | sort); do
    grep -E "^ALTER TABLE.*ADD CONSTRAINT.*CHECK" "$f" | while read line; do
        echo "- $line (\`$f\`)" >> "$OUTPUT_FILE"
    done
done
echo "" >> "$OUTPUT_FILE"

# 统计
TABLE_COUNT=$(grep -c "^CREATE TABLE" "$OUTPUT_FILE" || echo 0)
COLUMN_COUNT=$(grep -c "^ALTER TABLE.*ADD COLUMN" "$OUTPUT_FILE" || echo 0)
INDEX_COUNT=$(grep -c "^CREATE " "$OUTPUT_FILE" || echo 0)

echo "[DB Schema Gen] 统计: 表=$TABLE_COUNT, 列=$COLUMN_COUNT, 索引/约束=$INDEX_COUNT"
echo "[DB Schema Gen] 输出: $OUTPUT_FILE"

# CI 门禁: 与手写 md diff
if [ -f "docs/数据字典.md" ]; then
    if diff -q "$OUTPUT_FILE" "docs/数据字典.md" > /dev/null 2>&1; then
        echo "[DB Schema Gen] ✅ 数据字典与实际 schema 一致"
    else
        echo "[DB Schema Gen] ⚠ 数据字典与实际 schema 不一致, 请人工修订 docs/数据字典.md"
        diff "$OUTPUT_FILE" "docs/数据字典.md" | head -20
        exit 1
    fi
fi