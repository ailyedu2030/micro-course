-- S-004 安全增强 Phase 2: 清除 api_key 明文列
-- 
-- api_key_hash 列已通过 V319 迁移添加并回填（含唯一索引 idx_users_api_key_hash），
-- 所有查询/认证路径已迁移至 api_key_hash（SHA-256 比对），
-- 明文 api_key 不再被任何代码路径读取。
--
-- 安全理由：
--   1. api_key 明文存储在 DB 中违反了最小权限原则
--   2. 若 DB 备份泄露，明文 key 可被直接用于 Hermes API 认证
--   3. hash 已完全覆盖认证需求（SHA-256 不可逆，服务端仅做 hash 比对）
--
-- 兼容性：
--   - User.setApiKey() setter 不再写入明文（已修改为仅写 hash，设明文为 null）
--   - ApiKeyAuthenticationFilter：废除 findByApiKey() 明文回退，仅使用 hash 查询
--   - HermesWebhookManagementServiceImpl：同上
--   - AuthServiceImpl.getMyApiKey()：改用 apiKeyHash 判断 key 是否存在
--   - 旧的 api_key 列保留（NULLABLE），方便未来如有必要做数据审计

UPDATE users SET api_key = NULL WHERE api_key IS NOT NULL;
