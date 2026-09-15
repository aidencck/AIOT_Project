# ─── 阶段1：前端构建（node 多阶段，与 Java thin-jar 链路完全解耦）───
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ─── 阶段2：静态托管（nginx unprivileged，满足 PSS restricted 非 root）───
FROM nginxinc/nginx-unprivileged:1.27-alpine
# 官方镜像自动 envsubst 处理 /etc/nginx/templates/*.template（注入 ${ADMIN_API_UPSTREAM}）
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
# 聚合 API 上游默认指向网关（compose/helm 通过环境变量覆写）
ENV ADMIN_API_UPSTREAM=aiot-gateway:8080
# DNS resolver：docker 内置 DNS 默认 127.0.0.11；K8s 由 helm 注入 kube-dns
ENV ADMIN_API_RESOLVER=127.0.0.11
EXPOSE 8080
