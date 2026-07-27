# 数据库回滚脚本

此目录的 U-file 是历史遗留的"Flyway Undo"格式脚本。
Flyway 社区版不执行这些脚本，仅作为文档和人工回滚参考。

## 使用方法
1. DBA 需要回滚到某个版本时，人工执行对应的 U-file
2. 然后 DELETE FROM flyway_schema_history WHERE version='<target>';
3. 或参考 ROLLBACK_PLAN.md 的人工回滚流程
