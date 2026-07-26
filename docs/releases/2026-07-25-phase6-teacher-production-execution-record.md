# Phase 6 教师模块 Production 执行记录

> 执行日期：2026-07-25
> 执行方式：项目负责人明确授权后的生产发布
> 适用范围：Phase 6 教师模块正式环境切换

---

## 一、执行信息

| 项目 | 内容 |
|---|---|
| 执行人 | AI 总工程师（经项目负责人明确授权） |
| 生产主机 | `ubuntu@100.74.122.13` |
| 生产站点 | `https://microcourse.ailyedu.cn/` |
| 部署目标提交 | `d3c39bd70995f397672a3e111b4f31c526872701` |
| 提交标题 | `chore(release): add phase6 staging support (#130)` |
| 执行开始时间 | 2026-07-25 14:40 CST |
| 执行结束时间 | 2026-07-25 14:53 CST |
| 数据库变更 | 无 |
| 生产 DB 写操作 | 无 |

## 二、备份记录

| 项目 | 结果 | 证据 |
|---|---|---|
| 后端运行包备份 | 完成 | `/opt/micro-course/backups/20260725_144020/micro-course-api-1.0.0.jar` |
| 生产 compose 备份 | 完成 | `/opt/micro-course/backups/20260725_144020/docker-compose.yml` |
| 前端镜像备份 | 完成 | `micro-course-micro-course-admin:backup-20260725_144020` |
| 健康检查修复前 compose 备份 | 完成 | `/opt/micro-course/backups/docker-compose.yml.20260725_145219.healthcheck`、`/opt/micro-course/backups/docker-compose.yml.20260725_145231.healthcheck` |

## 三、执行步骤

| 步骤 | 结果 | 备注 |
|---|---|---|
| 上传后端 jar 到生产机 | 成功 | 上传到 `/tmp/micro-course-api-1.0.0.jar.20260725_144020` |
| 替换生产后端 jar | 成功 | 覆盖 `/opt/micro-course/micro-course-api-1.0.0.jar` |
| 后端热重载 | 成功 | `docker exec micro-course-micro-course-api-1 kill -s HUP 1` |
| 构建前端生产镜像 | 成功 | 最终使用 `linux/amd64` 镜像 |
| 上传前端镜像到生产机 | 成功 | `docker load` 后重新标记 `micro-course-micro-course-admin:latest` |
| 重建前端容器 | 成功 | `docker compose up -d micro-course-admin` |
| 修复前端健康检查 | 成功 | 将 `localhost` 改为 `127.0.0.1` 后再次重建容器 |

## 四、异常与处置

| 时间 | 现象 | 影响 | 处置 | 当前状态 |
|---|---|---|---|---|
| 2026-07-25 14:45 CST | 首次上传的前端镜像为 `arm64`，生产机为 `amd64` | admin 容器无法稳定启动 | 本地改为 `docker buildx build --platform linux/amd64 ... --load`，重新 `docker save/load` 并替换镜像 | 已解决 |
| 2026-07-25 14:50 CST | admin 容器对外可用，但 Docker health 为 `unhealthy` | 发布结果无法判定为最终完成 | 定位到 compose 中 `wget http://localhost:80/` 在容器内回环解析不稳定，改为 `http://127.0.0.1:80/` 后重建一次容器 | 已解决 |

## 五、发布后验证

### 5.1 容器健康

| 服务 | 结果 |
|---|---|
| `micro-course-micro-course-api-1` | `running + healthy` |
| `micro-course-micro-course-admin-1` | `running + healthy` |

### 5.2 外部可达性

| 检查项 | 结果 | 证据 |
|---|---|---|
| 生产首页 | 通过 | `curl -k -I -sS https://microcourse.ailyedu.cn/` 返回 `HTTP/2 200` |
| API 容器健康 | 通过 | `docker inspect` 显示 `health=healthy` |
| Admin 容器健康 | 通过 | `docker inspect` 显示 `health=healthy` |

### 5.3 观察窗口

| 项目 | 内容 |
|---|---|
| 观察开始时间 | 2026-07-25 14:48 CST |
| 观察结束时间 | 2026-07-25 14:53 CST |
| 后端错误日志 | 最近 5 分钟无新增 `ERROR / Exception / Caused by` |
| 前端错误日志 | 最近 5 分钟无新增 `error / exception / emerg / crit` |

## 六、发布结论

- 本次 Phase 6 教师模块已完成生产发布。
- 当前生产状态为：后端健康、前端健康、外部站点可达。
- 本次发布未执行生产数据库写操作，未引入 schema 变更。
- 建议继续保留 24 小时观察窗口，并按项目总控节奏在周报中补记运行情况。
