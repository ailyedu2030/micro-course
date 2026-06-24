# 微课管理平台 · 源代码保护 + 认证方案

> 版本：v1.0
> 日期：2026-06-24
> 状态：设计完成，待实施

---

## 一、设计目标

1. **源代码不离开开发机** — 服务器只跑编译产物
2. **核心业务逻辑后端化** — 前端不可绕过
3. **认证完备** — 用户登录 + Token 生命周期 + RBAC
4. **License 保护** — 防 Docker 镜像被复制到其他服务器
5. **审计可追** — 关键操作有日志

---

## 二、当前现状（已具备的保护）

| 维度 | 已实现 | 文件位置 |
|------|--------|----------|
| **后端编译产物部署** | ✅ Dockerfile 只 COPY `target/*.jar` | `micro-course-api/Dockerfile` |
| **前端编译产物部署** | ✅ Dockerfile 只 COPY `dist/` | `micro-course-admin/Dockerfile` |
| **Vite sourcemap** | ✅ 已关闭 `sourcemap: false` | `micro-course-admin/vite.config.js` |
| **JWT 认证** | ✅ jjwt 0.12.6，HMAC-SHA256，≥32 字节 | `util/JwtUtil.java` |
| **Spring Security** | ✅ 完整配置，CORS/CSP/HSTS/XSS | `config/SecurityConfig.java` |
| **密码加密** | ✅ BCrypt | `config/SecurityConfig.java` |
| **登录失败锁定** | ✅ Redis 计数 | `service/impl/AuthServiceImpl.java` |
| **Token 黑名单** | ✅ Redis 存储 | `service/impl/AuthServiceImpl.java` |
| **前端状态管理** | ✅ Pinia user store | `micro-course-admin/src/store/user.js` |
| **CORS 受控** | ✅ 白名单 origins | `SecurityConfig.java` |
| **HTTP 头安全** | ✅ CSP/HSTS/XSS/Referrer-Policy | `SecurityConfig.java` |
| **数据软删除** | ✅ users 表有 deleted_at | `docs/数据字典.md` |
| **API 契约文档** | ✅ Phase 1 v1.2 | `docs/API契约-Phase1.md` |
| **权限矩阵** | ✅ v3.1 | `docs/权限矩阵.md` |
| **审计日志规范** | ✅ 已编写 | `docs/操作日志规范.md` |

**结论**：核心安全机制已就位，但部署和强化流程需要补充。

---

## 三、源代码保护体系

### 3.1 部署边界（最重要）

**核心原则**：**源代码只在开发机上**，服务器只接收**编译后的产物**。

```
┌─────────────────────────────────────────────────────────────┐
│                       开发机 (Mac)                            │
│  /Users/jackie/微课平台/                                       │
│  ├── micro-course-api/   ← 完整源码                          │
│  ├── micro-course-admin/ ← 完整源码                          │
│  └── docs/                                                 │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ mvn package + npm run build
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  编译产物（不在 Git 中）                       │
│  ├── micro-course-api/target/*.jar                          │
│  └── micro-course-admin/dist/                               │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ scp 或 rsync（仅产物）
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                学校服务器（HP ProLiant DL388）                 │
│  /opt/micro-course/                                          │
│  ├── app.jar          ← 后端字节码                           │
│  ├── dist/            ← 前端静态资源                          │
│  ├── uploads/         ← 用户上传文件                          │
│  └── .env             ← 环境变量（含密钥）                    │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 .gitignore 强化（必须）

**防止源代码意外提交到 Git**：

```gitignore
# 编译产物（必须提交 jar 用于部署）
*.jar
!target/micro-course-api-*.jar
!target/app.jar

# 前端
dist/
node_modules/
.sourcemap
*.map

# 环境配置
.env
.env.local
.env.production
```

### 3.3 部署脚本（rsync/scp）

**核心约束**：
- ❌ **永远不要** `rsync -r micro-course-api/` 上传整个项目目录
- ✅ **只上传** `target/app.jar` + `dist/`

```bash
# 推荐：使用 rsync 排除一切
rsync -avz \
  --include='target/app.jar' \
  --include='dist/' \
  --include='dist/**' \
  --exclude='*' \
  ./ ubuntu@server:/opt/micro-course/

# 推荐：用 Dockerfile 构建后只导出镜像
docker build -t micro-course-api:1.0.0 .
docker save micro-course-api:1.0.0 | ssh server docker load
```

### 3.4 Java 字节码保护

**重要认知**：Spring 框架重度依赖反射（IoC、AOP、@Autowired、@Value、Jackson 序列化等），**ProGuard/R8 混淆会导致应用启动失败**。

**正确的策略**：
- ✅ **不混淆** — 让 Spring 正常运行
- ✅ **关键业务逻辑放后端** — 算法、规则、积分、权限判定都放 Java
- ✅ **API 返回最小字段** — 前端只拿到必要数据
- ✅ **敏感数据加密** — 数据库密码、JWT secret 都不在代码里
- ✅ **ProGuard 用于 jar 内部模块**（可选，针对纯计算模块）

```xml
<!-- pom.xml：可选，针对 utility 模块单独混淆 -->
<plugin>
    <groupId>com.github.wvengen</groupId>
    <artifactId>proguard-maven-plugin</artifactId>
    <version>2.6.1</version>
    <executions>
        <execution>
            <id>obfuscate-business</id>
            <phase>package</phase>
            <configuration>
                <obfuscate>true</obfuscate>
                <proguardVersion>6.2.2</proguardVersion>
                <injar>business-classes.jar</injar>
                <outjar>business-obfuscated.jar</outjar>
                <inFilter>com/microcourse/business/**</inFilter>
                <proguardInclude>proguard.conf</proguardInclude>
            </configuration>
            <goals><goal>proguard</goal></goals>
        </execution>
    </executions>
</plugin>
```

**推荐做法（更简单）**：**算法层面保护**

```java
// ❌ 错误：业务逻辑放前端
// 前端计算成绩、判定权限
function calculateGrade(score) { return score >= 60 ? 'PASS' : 'FAIL' }

// ✅ 正确：业务逻辑放后端
// 前端只发送请求，接收结果
const res = await api.post('/api/exam/grade', { score })
// 后端 Java 计算并记录审计
```

### 3.5 前端代码保护

**Vite 已配置的强化**：

```js
// vite.config.js
export default defineConfig({
  build: {
    sourcemap: false,        // ✅ 已关闭（生产环境）
    minify: 'esbuild',       // ✅ 默认 minify
    cssCodeSplit: true,
    rollupOptions: {
      output: {
        // ✅ 已分 chunk
        manualChunks: {
          'vendor-element-base': ['element-plus'],
          'vendor-element-icons': ['@element-plus/icons-vue'],
          'vendor-xlsx': ['xlsx'],
          'vendor-video': ['./src/views/student/VideoPlayer.vue'],
        }
      }
    }
  }
})
```

**强化建议**：

```js
// vite.config.js 增强
build: {
  sourcemap: false,
  minify: 'terser',  // 比 esbuild 压缩率更高
  terserOptions: {
    compress: {
      drop_console: true,      // 删除 console
      drop_debugger: true,     // 删除 debugger
      pure_funcs: ['console.log', 'console.info']
    },
    mangle: {
      safari10: true
    }
  },
  rollupOptions: {
    output: {
      manualChunks(id) {
        if (id.includes('node_modules')) {
          return 'vendor'
        }
      }
    }
  }
}
```

### 3.6 数据库保护

**约束**：

1. **数据库密码不在代码里** — 通过环境变量
2. **SQL 注入防护** — MyBatis 参数化（已实现）
3. **数据库不直接对外** — 只通过后端服务访问
4. **敏感字段加密** — 用户密码 BCrypt（已实现）

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/micro_course
    username: ${DB_USERNAME}      # 来自 .env，不在代码
    password: ${DB_PASSWORD}      # 来自 .env，不在代码
```

### 3.7 License 保护（防 Docker 镜像被复制）

**服务器端 License 校验**：

```java
// LicenseCheckFilter.java（新增）
@Component
public class LicenseCheckFilter extends OncePerRequestFilter {
    
    @Value("${license.key}")
    private String licenseKey;
    
    @Value("${license.expiry:2027-12-31}")
    private String expiryDate;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain chain) {
        if (!isValidLicense()) {
            response.setStatus(403);
            response.getWriter().write("License expired or invalid");
            return;
        }
        chain.doFilter(request, response);
    }
    
    private boolean isValidLicense() {
        // 1. 校验 license key
        // 2. 检查到期日期
        // 3. 校验服务器 MAC 地址
        // 4. 定期心跳到 license server
        return true;
    }
}
```

```java
// LicenseGenerator.java（开发机使用）
public class LicenseGenerator {
    public static String generate(String macAddress, Date expiry) {
        String payload = macAddress + ":" + expiry.getTime();
        return Base64.encode(HMAC_SHA256(payload, SECRET));
    }
}
```

---

## 四、认证体系强化

### 4.1 当前认证（已实现）

```
登录请求 → AuthController.login()
  ↓
AuthServiceImpl:
  1. 校验图形验证码（防机器人）
  2. Redis 检查 login:lock:{username}（防爆破）
  3. 查询用户 + BCrypt 校验密码
  4. 检查用户状态（禁用/软删除）
  5. 生成 accessToken + refreshToken
  6. 记录 lastLoginAt + IP + UA
  ↓
返回 LoginResponse { accessToken, refreshToken }
  ↓
前端存 token 到 localStorage + Pinia store
  ↓
axios interceptor 自动加 Authorization: Bearer
  ↓
JwtAuthenticationFilter 校验 token
  ↓
UserStatusCheckFilter 校验用户当前状态
```

### 4.2 强化项（推荐实施）

| 项 | 当前 | 建议强化 |
|----|------|----------|
| **登录失败锁定** | ✅ Redis 计数 | 5 次锁定 30 分钟 |
| **Token 黑名单** | ✅ Redis | TTL 自动清理 |
| **refreshToken 轮换** | ✅ 已实现 | - |
| **图形验证码** | ✅ 已实现 | - |
| **JWT 算法** | ✅ HMAC-SHA256 | 可升级 Ed25519（更高安全） |
| **2FA（双因素）** | ❌ 未实现 | **新增**（管理员强制） |
| **登录设备管理** | ❌ 未实现 | **新增**（显示活跃设备） |
| **异常登录检测** | ❌ 未实现 | **新增**（异地 IP 告警） |
| **密码策略** | ⚠️ 仅 BCrypt | **强化**（≥8位 + 复杂度） |
| **会话超时** | ✅ 7 天 refresh | 可缩短到 4 小时 access |

### 4.3 新增功能：管理员登录增强

```java
// AdminAuthService.java
@Service
public class AdminAuthService {
    
    // 管理员登录必须 2FA
    public LoginResponse loginWith2FA(String username, String password, String totpCode) {
        // 1. 验证用户名密码
        // 2. 验证 TOTP（Google Authenticator）
        // 3. 生成 token
    }
    
    // 检测异常登录
    public void checkAnomalyLogin(Long userId, String ip, String ua) {
        // 1. 检查 IP 地理位置（上次登录城市 vs 这次）
        // 2. 检查设备指纹
        // 3. 异常时发送邮件/短信告警
    }
}
```

### 4.4 前端登录流程优化

```javascript
// store/user.js
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('accessToken') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    userInfo: null,
    // 新增：设备指纹
    deviceFingerprint: getDeviceFingerprint()
  }),
  
  actions: {
    async login(credentials) {
      // 1. 图形验证码
      // 2. 用户名密码
      // 3. （管理员）TOTP 验证码
      // 4. 存储 token
    },
    
    async refreshAccessToken() {
      // 用 refreshToken 换 accessToken
    },
    
    async logout() {
      // 1. 调用后端 logout（黑名单 token）
      // 2. 清本地存储
      // 3. 跳转登录页
    }
  }
})
```

---

## 五、API 安全

### 5.1 已实现

```java
// SecurityConfig.java 摘录
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/login", "/api/auth/cas", 
                     "/api/auth/refresh", "/api/auth/register").permitAll()
    .requestMatchers("/api/files/covers/**", "/api/files/avatars/**",
                     "/api/files/banners/**", "/api/files/system/**").permitAll()
    .anyRequest().authenticated()
)
```

### 5.2 强化：API 限流（防爬虫 + 防爆破）

**新增 Bucket4j 限流**：

```java
// RateLimitConfig.java
@Configuration
public class RateLimitConfig {
    
    @Bean
    public Bucket loginBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
            .build();
    }
    
    @Bean
    public Bucket apiBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
            .build();
    }
}

// RateLimitFilter.java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String key = getClientIp(request) + ":" + request.getRequestURI();
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(request));
        
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);  // Too Many Requests
            response.getWriter().write("Rate limit exceeded");
        }
    }
}
```

### 5.3 SQL 注入防护（已实现）

MyBatis Plus 默认使用参数化查询：

```java
// ✅ 安全：参数化
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, username);
userMapper.selectOne(wrapper);

// ❌ 危险：字符串拼接（项目应避免）
@Select("SELECT * FROM users WHERE username = '" + username + "'")
```

---

## 六、关键决策

### 决策 1：是否做 Java 字节码混淆？

**结论**：❌ **不做全量混淆**

**理由**：
- Spring 框架重度依赖反射（AOP、IoC、@Autowired、@Value 等）
- 混淆会导致运行时 ClassNotFoundException、NoSuchMethodError
- ProGuard 需要写大量 keep 规则，维护成本极高
- 即使混淆了，反编译仍然可以看到逻辑（只是变量名变了）

**替代方案**：**业务逻辑后端化** + **关键计算服务端完成**

### 决策 2：是否做 License 校验？

**结论**：✅ **做简单 License 校验**

**理由**：
- 防止 Docker 镜像被复制到其他学校
- 实现简单（几行代码）
- 可绑定 MAC 地址 + 到期日期
- 失效后服务自动停止

### 决策 3：是否启用 2FA？

**结论**：✅ **仅管理员启用 2FA**

**理由**：
- 学生 / 教师：用户体验优先
- 管理员：操作敏感（用户管理、权限管理），必须 2FA

### 决策 4：API 限流策略

**结论**：✅ **基于 IP + 用户限流**

- 登录：5 次/分钟/IP
- 普通 API：100 次/分钟/用户
- 文件上传：10 次/小时/用户
- 导出：5 次/小时/用户

---

## 七、部署检查清单

### 部署前

- [ ] 代码已提交并 push 到 Git
- [ ] `mvn package` 成功，无 ERROR
- [ ] `npm run build` 成功，dist/ 生成
- [ ] `.env` 包含所有密钥（DB_PASSWORD、JWT_SECRET、VIDEO_SIGN_SECRET、LICENSE_KEY）
- [ ] `.gitignore` 已强化（防止 jar/dist 提交）

### 部署时

- [ ] **只上传** `target/app.jar` + `dist/` + `.env`
- [ ] **不上传** `src/`、`node_modules/`、`pom.xml`、`.git/`
- [ ] 服务器端 `.env` 文件权限 600
- [ ] 服务器 jar 包 SHA256 校验

### 部署后

- [ ] 后端 `/api/admin/stats/health` 返回 200
- [ ] 前端首页加载正常
- [ ] 登录测试通过（admin / student / teacher）
- [ ] Token 生命周期测试（access 过期 + refresh 续期）
- [ ] License 校验通过
- [ ] 审计日志记录部署操作

---

## 八、紧急情况预案

### 服务器被入侵

1. **立即吊销所有 JWT secret** — 修改 `JWT_SECRET` 让所有 token 失效
2. **重置所有用户密码** — 强制下次登录改密
3. **审查审计日志** — 找入侵路径
4. **清除 Docker 镜像** — 重新部署（从开发机构建）

### 源码泄露

1. **撤销泄露的 jar 部署** — 立即停止服务
2. **分析泄露内容** — 哪些业务逻辑暴露
3. **重构暴露的算法** — 移到更难反编译的地方
4. **加强 License 校验** — 让复制镜像无法运行

### License 过期

1. **自动停止服务** — LicenseCheckFilter 返回 403
2. **通知学校** — 邮件/短信
3. **续费流程** — 更新 license key

---

## 九、实施步骤

### Phase 1：基础保护（已完成）
- ✅ JWT 认证
- ✅ Spring Security
- ✅ BCrypt 密码
- ✅ Dockerfile 只部署编译产物
- ✅ Vite sourcemap 关闭

### Phase 2：认证强化（待实施）
- [ ] 2FA（管理员）
- [ ] API 限流（Bucket4j）
- [ ] 异常登录检测
- [ ] 设备指纹

### Phase 3：版权保护（待实施）
- [ ] License 校验机制
- [ ] MAC 地址绑定
- [ ] 到期自动停止

### Phase 4：审计追溯（待实施）
- [ ] 关键操作日志
- [ ] 登录审计
- [ ] 异常告警

---

## 十、参考文档

- Spring Security 官方文档：https://docs.spring.io/spring-security/reference/
- JWT 最佳实践：https://datatracker.ietf.org/doc/html/rfc8725
- OWASP Top 10：https://owasp.org/www-project-top-ten/
- Vite 生产构建：https://vitejs.dev/guide/build.html
- 项目权限矩阵：`docs/权限矩阵.md`
- 项目 API 契约：`docs/API契约-Phase1.md`