# 学校 IT 管理员部署指南：微课管理平台外网访问

> 适用对象：学校网络中心 / 信息中心 / 负责服务器和网络的 IT 同事
> 服务名称：微课管理平台
> 当前状态：已在内网 192.168.12.155 部署完成，4 个 Docker 容器运行中（postgres / redis / api / admin）
> 目标：让师生从外网能访问到平台

---

## 0. 关键信息（请先看完这部分）

| 项目 | 值 | 说明 |
|------|------|------|
| **服务器内网 IP** | `192.168.12.155` | 服务器的实际地址 |
| **学校出口公网 IP** | `?` | **需要您查询并填入** |
| **前端端口** | `80` | 浏览器访问 |
| **后端 API 端口** | `8080` | 仅前端需要调用 |
| **当前网络拓扑** | 内网 | 仅校内访问 |
| **DNS 域名** | `?` | 推荐 `course.school.edu.cn` |
| **数据库** | PostgreSQL 17 | 已运行，4 天未宕机 |
| **缓存** | Redis 7 | 已运行 |
| **服务器 SSH** | 22 | ubuntu 用户 |

**确认检查清单（部署前请 IT 回答）：**
- [ ] 学校的公网 IP 是多少？（在出口路由器上查）
- [ ] 学校 DNS 服务器是哪个？（方便加 A 记录）
- [ ] 是否有学校主域名？（如 `school.edu.cn`）
- [ ] 出口路由器品牌/型号？（H3C / 华为 / 锐捷 / Cisco）
- [ ] 是否允许对外开放 80 端口？（部分学校只允许教育网访问）

---

## 1. 总体目标

```
【修改前 - 当前状态】
外部用户 ──(无法访问)──> 学校防火墙
                              │
                              ├─ 192.168.12.155:80  ❌ 不开放
                              ├─ 192.168.12.155:8080 ❌ 不开放
                              └─ 100.74.122.13 ❌ Tailscale 私网

【修改后 - 目标状态】
外部用户 ──> 学校公网IP:80 ──> 防火墙(端口映射) ──> 192.168.12.155:80 ──> 前端 nginx
              │                    │
              │                    └─ 公网IP:8080 ──> 192.168.12.155:8080 ──> 后端 API
              │
              └─ DNS: course.school.edu.cn ──> 公网IP
```

---

## 2. IT 需要做的 4 件事（按顺序）

### 任务 1：查询学校公网 IP

**操作步骤：**

1. 登录学校出口路由器/防火墙管理后台
2. 找到 WAN 口（外网口）IP
3. 或运行命令 `curl ifconfig.me` 在服务器上查

**所需信息：**
- 学校公网 IP：`_______________`（请填入）

**记录学校出口设备的公网 IP 备用。**

---

### 任务 2：路由器/防火墙端口映射

#### 2.1 路由器/防火墙配置

| 项目 | 配置 |
|------|------|
| 外部端口 | `80` (HTTP) |
| 内部 IP | `192.168.12.155` |
| 内部端口 | `80` (Nginx) |
| 协议 | TCP |
| 描述 | 微课平台前端 |

如果**只暴露 80**（前端），Nginx 反向代理到 8080：
- 用户访问 `http://公网IP/` → Nginx 返回前端
- 前端调用 `/api/*` → Nginx 反代到 `192.168.12.155:8080`

#### 2.2 不同厂商的端口映射配置

**H3C 路由器：**
```
<H3C> system-view
[H3C] nat static source global X.X.X.X inside 192.168.12.155
[H3C] interface GigabitEthernet0/0/1
[H3C-GigabitEthernet0/0/1] nat outbound 80
```

**华为防火墙：**
```
[FW] firewall zone trust
[FW-zone-trust] add interface GigabitEthernet0/0/1
[FW] nat server zone untrust protocol tcp global X.X.X.X 80 inside 192.168.12.155 80
```

**Cisco ASA：**
```
ASA(config)# object network SERVER-WEB
ASA(config-network-object)# host 192.168.12.155
ASA(config-network-object)# nat (inside,outside) static 192.168.12.155 interface
ASA(config)# access-list OUTSIDE-IN permit tcp any object SERVER-WEB eq 80
```

**H3C / 锐捷 / 华为（校园网通用）：**
```
1. 进入"地址转换" → "NAT" → "内部服务器"
2. 添加：IP=192.168.12.155, 协议=TCP, 内部端口=80
3. 外部端口=80
4. 保存
5. 同步添加"安全策略" → 放行 80 端口入站
```

---

### 任务 3：防火墙策略

| 方向 | 源 | 目的 | 协议 | 动作 |
|------|------|------|------|------|
| 入站 | `any` | `192.168.12.155:80` | TCP | 允许 |
| 入站 | `any` | `192.168.12.155:8080` | TCP | 允许（如果直接暴露 API） |
| 出站 | `192.168.12.155` | `any` | TCP | 允许（用于镜像拉取、SSL 证书更新）|

**H3C 防火墙配置：**
```
[H3C] security-policy ip
[H3C-security-policy-ip] rule name micro-course-platform
[H3C-security-policy-ip-rule-micro-course-platform] source-zone untrust
[H3C-security-policy-ip-rule-micro-course-platform] destination-zone dmz
[H3C-security-policy-ip-rule-micro-course-platform] destination-address 192.168.12.155 32
[H3C-security-policy-ip-rule-micro-course-platform] service tcp protocol tcp destination-port 80
[H3C-security-policy-ip-rule-micro-course-platform] action permit
```

---

### 任务 4：DNS 域名配置

#### 4.1 推荐方案：学校主域名

| 子域名 | 用途 | 解析 |
|--------|------|------|
| `course.school.edu.cn` | 主入口 | A 记录 → 公网 IP |
| `api.course.school.edu.cn` | API 入口（可选） | A 记录 → 公网 IP |

**配置步骤：**

1. 登录学校 DNS 管理后台（如 `https://dns.school.edu.cn` 或 rfc.zhuanjia.cn）
2. 进入 `school.edu.cn` 区域
3. 添加 A 记录：
   - 主机名：`course`
   - 类型：A
   - 值：`<公网 IP>`（从任务 1 获取）
   - TTL：600
4. 保存
5. 验证（IT 自己先查）：
   ```
   nslookup course.school.edu.cn
   ```

#### 4.2 没学校域名的临时方案

如果学校没有可用的主域名，用一个**临时子域名**：
- 向互联网域名注册商申请 `microschool.cn` 或 `course-edu.cn`（约 50-80 元/年）
- 解析到学校公网 IP

**或者跳过域名**，用 IP 直接访问：
- 师生收藏 `http://公网IP/`
- 但浏览器会显示"不安全"（HTTP 不是 HTTPS）

---

## 3. 验证清单（IT 完成上述操作后）

在服务器上执行：

```bash
# 1. 公网 IP 验证
curl ifconfig.me
# 输出应该是学校公网 IP

# 2. 端口验证
curl -I http://<公网IP>/
# 应返回 200 OK

# 3. 域名解析
nslookup course.school.edu.cn
# 应返回学校公网 IP

# 4. 从外网测试（在 IT 自己电脑上）
浏览器访问 http://course.school.edu.cn/
或 http://<公网IP>/
```

**全部通过 → 外网访问已配置完成。**

---

## 4. SSL 证书（可选但强烈推荐）

HTTP 浏览器会显示"不安全"，师生会不信任。配置 HTTPS：

### 4.1 选项 A：学校自签名证书

仅适合内部测试。浏览器会显示警告。

```bash
# 在服务器上生成（IT 执行）
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /etc/nginx/ssl/course.key \
  -out /etc/nginx/ssl/course.crt \
  -subj "/CN=course.school.edu.cn"
```

上传到 `192.168.12.155:/opt/micro-course/ssl/`

### 4.2 选项 B：Let's Encrypt（推荐，免费）

需要域名可公网解析。IT 在服务器上执行：

```bash
# 1. 申请证书（首次）
sudo apt install certbot
sudo certbot certonly --standalone -d course.school.edu.cn

# 2. 自动续期（cron 任务）
echo "0 0 1 * * certbot renew" | sudo crontab -
```

证书路径：`/etc/letsencrypt/live/course.school.edu.cn/`

### 4.3 选项 C：学校已有证书

如果学校已有 wildcard 证书（如 `*.school.edu.cn`），直接复用：

```bash
# 上传到服务器
scp /path/to/school.crt ubuntu@192.168.12.155:/opt/micro-course/ssl/
scp /path/to/school.key ubuntu@192.168.12.155:/opt/micro-course/ssl/
```

### 4.4 我部署 SSL（需要证书准备好后）

联系 IT 提供证书（或域名+让我用 Let's Encrypt），我会完成：
- 修改 nginx.conf 添加 443 监听 + SSL 配置
- HTTP 自动跳转 HTTPS
- 设置 HSTS 头

---

## 5. 备份策略（IT 协助）

当前服务器已有每日自动备份（凌晨 3 点），保留 7 天。

**IT 协助：异地备份**（推荐）

### 5.1 推送到学校备份服务器

```bash
# 在学校备份服务器上（IT 提供 SSH 凭据）
# 添加 cron：
0 4 * * * rsync -avz ubuntu@192.168.12.155:/opt/micro-course/backups/ /school-backup/micro-course/
```

### 5.2 推送到对象存储（阿里云 OSS / 腾讯云 COS）

IT 提供 AccessKey 后，我配置自动上传：

```bash
# /opt/micro-course/scripts/backup-to-oss.sh
#!/bin/bash
TODAY=$(date +%Y%m%d)
ossutil cp /opt/micro-course/backups/postgres-$TODAY.sql.gz \
  oss://school-backup/micro-course/database/
ossutil cp /opt/micro-course/backups/redis-$TODAY.rdb \
  oss://school-backup/micro-course/cache/
```

---

## 6. 监控告警（IT 协助）

### 6.1 基础监控（IT 帮接学校监控平台）

服务器暴露的监控端点（已配置）：

| 端点 | 用途 | 端口 |
|------|------|------|
| `http://192.168.12.155:8080/actuator/health` | 健康检查 | 8080 |
| `http://192.168.12.155:8080/actuator/prometheus` | Prometheus 指标 | 8080 |

**IT 接入学校 Zabbix/Prometheus：**
```yaml
# /etc/zabbix/zabbix_agent2.d/micro-course.conf
# 健康检查
WebCheck[course_health]
  url=http://192.168.12.155:8080/actuator/health
  status_codes=200
  interval=30s
```

### 6.2 推荐告警阈值

| 指标 | 阈值 | 动作 |
|------|------|------|
| 容器 down | 任一容器 not healthy | 短信 + 钉钉 |
| CPU > 80% | 持续 5 分钟 | 邮件 |
| 内存 > 85% | 持续 5 分钟 | 邮件 |
| 磁盘 < 10% | 立即 | 短信 |

---

## 7. 应急联系

| 角色 | 姓名 | 电话 | 邮箱 |
|------|------|------|------|
| 系统负责人 | _____ | _____ | _____ |
| IT 主管 | _____ | _____ | _____ |
| 服务器位置 | 192.168.12.155 机房 _____  机柜 _____ | | |
| 应急恢复 | 见第 5 节备份策略 | | |

---

## 8. IT 协助清单（请按顺序确认）

- [ ] **任务 1**：查询学校公网 IP 并填入第 0 节表格
- [ ] **任务 2.1**：在路由器/防火墙加端口映射（80 → 192.168.12.155:80）
- [ ] **任务 2.2**：开放防火墙 80 端口入站规则
- [ ] **任务 3**：DNS 加 `course.school.edu.cn` A 记录 → 公网 IP
- [ ] **任务 4**：决定 SSL 方案（自签 / Let's Encrypt / 复用学校证书）
- [ ] **任务 5**：协助配置异地备份
- [ ] **任务 6**：协助接入学校监控平台
- [ ] **任务 7**：在外网测试访问（自己电脑浏览器测试 `course.school.edu.cn`）
- [ ] **任务 8**：完成 IT 应急联系表

**全部完成 → 外网访问正式开通。**

---

## 附录 A：常见问题

**Q1：80 端口已被学校其他系统占用？**
A：用 8080 端口映射到 80，师生访问 `http://公网IP:8080/`。或用 nginx 反代转发到非常规端口。

**Q2：学校不允许外网访问 80？**
A：用 8443（HTTPS）或 8888 等高位端口。需师生记住端口。

**Q3：学校有 Web 应用防火墙（WAF）？**
A：WAF 需配置为白名单。`/api/*` 路径放行 JSON POST。

**Q4：DNS 在哪改？**
A：学校 DNS 通常是 BIND 9 + DLZ。联系 DNS 管理员（通常是网管中心）。如学校用 `万网`/`DNSPod`，需管理员账号。

**Q5：备案？**
A：网站对外提供信息服务需 ICP 备案。**但本平台是内网教学系统**，按教育部规定，校内网可不开 ICP。但师生用外网访问时，理论上需要。如果学校在教育网内，**不需 ICP**（教育网是特殊网络）。如从公网访问且学校有公网 IP，**可能需要 ICP**。建议先问学校"信息中心"，看历史项目如何处理。

**Q6：多大带宽？**
A：500 节课 × 100 学生 × 5 GB/课 = 250 GB 流量，足够。**实际并发**：
- 普通上课：50 人同时访问 ≤ 50 Mbps
- 在线考试：500 人同时交卷 ≤ 200 Mbps
- 学校 500 Mbps 公网足够

---

## 附录 B：IT 可参考的运维命令

```bash
# SSH 登录
ssh ubuntu@192.168.12.155

# 查看服务状态
docker ps
docker logs micro-course-micro-course-api-1 --tail 50

# 重启服务
cd /opt/micro-course
docker compose restart

# 实时查看流量
docker stats --no-stream

# 数据库连接数
docker exec micro-course-postgres-1 psql -U microcourse -d micro_course \
  -c "SELECT count(*) FROM pg_stat_activity;"

# 备份当前数据库
docker exec micro-course-postgres-1 pg_dump -U microcourse micro_course | gzip > /tmp/backup-$(date +%s).sql.gz

# 恢复备份
zcat /tmp/backup-XXX.sql.gz | docker exec -i micro-course-postgres-1 psql -U microcourse micro_course

# 进入容器调试
docker exec -it micro-course-micro-course-api-1 sh
```

---

## 附录 C：联系开发方

部署负责人：系统管理员（开发方）
- 邮箱：待填
- 微信：待填
- GitHub：待填

部署前请阅读 `README.md` 和 `DEPLOYMENT_CHECKLIST.md`。

---

*文档版本：v1.0 | 编制日期：2026-06-27 | 适用范围：学校内网微课管理平台外网访问*
