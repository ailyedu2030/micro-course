# =============================================================================
# Dockerfile · 微课管理平台前端
# 多阶段构建: 编译 → Nginx 静态服务
# =============================================================================

# ---- Stage 1: 编译 ----
FROM node:20-alpine AS builder
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- Stage 2: Nginx ----
FROM nginx:alpine
WORKDIR /usr/share/nginx/html
COPY --from=builder /build/dist/ .

# Nginx 配置注入
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://localhost/ || exit 1
