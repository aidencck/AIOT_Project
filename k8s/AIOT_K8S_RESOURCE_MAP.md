# AIoT 平台 K8s 化资源映射与逻辑关系 v1.0

## 1 目标

把 `docker-compose.yml` 中的 12 类服务（4 中间件 + 8 业务 + 6 可观测）精确映射到三层：

```
L0 云厂商基础资源 (IaaS/PaaS)
  └── L1 Kubernetes 集群控制面与节点池
        └── L2 AIoT 项目工作负载 (Namespace / Workload / Config)
```

**硬约束：**
- 所有 `container_name: aiot-*` → 同一 `Namespace: aiot`
- 所有 `depends_on: service_healthy` → K8s `initContainer` + TCP 就绪门控（杜绝跨层假设）
- 所有有状态服务（MySQL/Redis/Nacos/EMQX/Loki/Tempo/Prometheus/Grafana）→ `StatefulSet` + `PersistentVolumeClaim` 绑定云盘
- 所有 `${VAR}` 环境变量 → `Secret` / `ConfigMap` 注入，不允许 bake 进镜像
- 所有对外暴露端口 → Ingress（HTTP/WS），TCP 类端口（1883 MQTT）→ `LoadBalancer` Service 或云厂商 LB

---

## 2 三层资源映射总表

| 层级 | 资源类型 | 实例 | 归属 | 计费项 | 交付物 |
|---|---|---|---|---|---|
| L0 云资源 | VPC / VSwitch / SecurityGroup | `vpc-aiot` `sg-aiot-app` `sg-aiot-db` | 云账号 / 项目 VPC | 带宽 / 流量 | 云上 IaC (Terraform/Pulumi) |
| L0 | 托管 RDS MySQL | `aiot-mysql-80` | 生产 DBA 域 | RDS 规格 + 存储 | IaC + `k8s/base/secrets.yaml` (endpoint/pwd) |
| L0 | 托管 Redis (Cluster) | `aiot-redis-7` | DBA 域 | 分片数 + 内存 | IaC + ConfigMap (host/port) |
| L0 | 托管 K8s (EKS/ACK/GKE) | `k8s-aiot-<env>` | 平台 SRE 域 | Master + Node 小时费 | IaC `kubeconfig` |
| L0 | 对象存储 OSS/S3 | `aiot-grafana-snap` `aiot-ota-firmware` | 业务域 | 存储容量 + 请求 | IaC bucket 清单 |
| L0 | SSL 证书 / DNS | `*.aiot.example.com` | 安全 / 平台域 | 证书费 + DNS | IaC `cert-manager` Issuer |
| L1 K8s | Node Pool | `pool-db-4c16g` `pool-svc-8c32g` `pool-obs-4c16g` | 集群 | Node 规格 | IaC nodepool 声明 |
| L1 | StorageClass | `sc-ssd-rwo` (云盘) `sc-nas-rwx` (共享) | 集群 | 云盘 IOPS | `kubectl get sc` |
| L1 | IngressClass | `nginx-internal` `nginx-external` | 集群 | LB 小时费 + 带宽 | `helm install ingress-nginx` |
| L1 | Namespace | `aiot` `aiot-ops` (仅可观测可选) | 项目 | - | `k8s/base/namespace.yaml` |
| L1 | ResourceQuota / LimitRange | `rq-aiot-svc` `lr-aiot-svc` | 项目 | - (配额保护) | `k8s/overlays/<env>/_quota.yaml` |
| L2 项目工作负载 | StatefulSet(有状态) | mysql / redis / nacos / emqx / loki / tempo / prometheus / grafana | L2 `aiot` | 集群内 CPU/Mem + 云盘 PVC | `k8s/base/infra/*.yaml` `k8s/base/observability/*.yaml` |
| L2 | Deployment(无状态) + HPA | gateway auth-service device-service home-service rule-engine shadow-service mqtt-adapter data-parser | L2 `aiot` | 集群内 CPU/Mem | `k8s/base/services/*.yaml` HPA CPU 70% Mem 80% |
| L2 | Service | ClusterIP 内部、LoadBalancer (1883 MQTT 出口) | L2 | LB (仅出口) | 同上各文件 |
| L2 | ConfigMap | `aiot-common-config` `aiot-service-urls` `prometheus-config` `*`-config | L2 | - | `k8s/base/configmaps.yaml` + 各配置 CM |
| L2 | Secret | `aiot-secrets` (Opaque) / `tls-aiot-ingress` (TLS) | L2 + cert-manager | - | `k8s/base/secrets.yaml` |
| L2 | PVC | `*-data` × 9 | L1 StorageClass | 云盘容量 | `k8s/base/pvcs.yaml` |
| L2 | Ingress | `aiot-ingress` | L1 IngressClass | L0 带宽/LB | `k8s/base/ingress.yaml` |

---

## 3 依赖关系 DAG（严格按启动门控）

**3.1 云资源必须先于 L1/L2（IaaS 依赖门禁）**
```
L0 VPC/SG/DNS/Cert ──▶ L0 K8s Cluster + RDS + Redis + OSS
                              │
                              ▼
                L1 NodePool + StorageClass + IngressClass
                              │
                              ▼
                    L2 Namespace + Quota + Secrets/CM
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
   mysql (RDS/自带)     nacos (需要MySQL?)     redis (集群/自带)
         │                    │                    │
         ▼                    ▼                    ▼
      emqx ────────────  8 业务服务  ───────────  observability
                            │
                            ▼
                        ingress → 外部流量
```

**3.2 L2 项目内服务间 `depends_on` 精确转录（来自 docker-compose.yml）**

| 消费者 | `depends_on` 项 | K8s 实现位置 |
|---|---|---|
| gateway | `nacos: healthy` | `gateway.yaml` initContainer `nc -z nacos 8848` |
| home-service | `mysql:healthy` + `redis:healthy` | `home-service.yaml` initContainer 等待 `mysql:3306 redis:6379` |
| rule-engine | `nacos:healthy` + `redis:healthy` | `rule-engine.yaml` |
| shadow-service | `nacos:healthy` + `redis:healthy` | `shadow-service.yaml` |
| mqtt-adapter | `nacos:healthy` + `data-parser: started` | `mqtt-adapter.yaml` 等待 `nacos:8848 data-parser:8086` |
| data-parser | `nacos:healthy` + `redis:healthy` | `data-parser.yaml` |
| device-service | `nacos + mysql + redis + emqx` 全部 healthy + `home-service started` | `device-service.yaml` 等待 4 端口 + home-service |
| auth-service | `nacos + mysql + redis` healthy + `device-service healthy` | `auth-service.yaml` 等待 3 端口 + device-service:8081 |
| prometheus | 全部 8 业务 started | 不做硬门控，改为 `scrape_configs` 失败重试 |

**原则：** Compose 中 `service_healthy` → `readinessProbe` + initContainer 双保险；`service_started` → 仅 initContainer 端口通。

---

## 4 资源配额与容量预算（硬数字门禁）

**4.1 单 Pod 规格（来自 compose `JAVA_OPTS` + 保守 × 余量）**

| 工作负载 | 副本(dev/stg/prod) | CPU requests | CPU limits | Mem req | Mem lim | PVC 大小 |
|---|---|---|---|---|---|---|
| mysql (StatefulSet) | 1/1/1 | 500m | 2 | 1Gi | 4Gi | 20Gi (可扩 200Gi) |
| redis (StatefulSet) | 1/1/1 | 250m | 1 | 512Mi | 2Gi | 8Gi |
| nacos (StatefulSet) | 1/1/1 | 500m | 2 | 1Gi | 2Gi | 10Gi |
| emqx (StatefulSet) | 1/1/3(集群) | 500m×N | 2×N | 1Gi×N | 3Gi×N | 10Gi×N |
| gateway (Dep+HPA) | 1/2/3 | 250m | 1 | 512Mi | 1Gi | - |
| auth-service | 1/2/3 | 250m | 1 | 512Mi | 1Gi | - |
| device-service | 1/3/5 | 500m | 2 | 1Gi | 2Gi | - |
| home-service | 1/2/3 | 250m | 1 | 512Mi | 1Gi | - |
| rule-engine | 1/3/5 | 500m | 2 | 1Gi | 2Gi | - |
| shadow-service | 1/2/3 | 250m | 1 | 512Mi | 1Gi | - |
| mqtt-adapter | 1/2/3 | 250m | 1 | 512Mi | 1Gi | - |
| data-parser | 1/2/3 | 250m | 1 | 512Mi | 1Gi | - |
| prometheus | 1/1/1 | 500m | 2 | 1Gi | 4Gi | 30Gi |
| alertmanager | 1/1/1 | 100m | 500m | 128Mi | 512Mi | - |
| loki (StatefulSet) | 1/1/1 | 250m | 1 | 512Mi | 2Gi | 20Gi |
| tempo (StatefulSet) | 1/1/1 | 250m | 1 | 512Mi | 2Gi | 10Gi |
| promtail (DaemonSet) | 每 Node 1 | 100m/node | 500m/node | 128Mi/node | 512Mi/node | 1Gi PVC per DS host |
| grafana | 1/1/1 | 250m | 1 | 512Mi | 1Gi | 5Gi |

**4.2 集群节点池需求（生产 baseline，不含云托管 RDS/Redis）**

| 节点池 | 用途 | 实例规格(vCPU×Mem) | 节点数 | 挂载本地盘 | 打标 taint |
|---|---|---|---|---|---|
| `pool-db` | 4 有状态中间件 (mysql redis nacos emqx) | 8c × 32g | 3 (反亲和每个 StatefulSet Pod 分节点) | 云盘 SSD | `dedicated=db:NoSchedule` |
| `pool-svc` | 8 业务服务 + gateway | 16c × 64g | 5 (HPA 扩到上限 28 Pod) | - | - |
| `pool-obs` | prom/loki/tempo/grafana/promtail | 8c × 32g | 3 | 云盘高 IOPS | `dedicated=obs:NoSchedule` |

**4.3 Namespace 级 ResourceQuota（生产门禁）**

```yaml
apiVersion: v1
kind: ResourceQuota
metadata: {name: rq-aiot, namespace: aiot}
spec:
  hard:
    requests.cpu: "120"
    requests.memory: "256Gi"
    limits.cpu:   "300"
    limits.memory: "600Gi"
    persistentvolumeclaims: "32"
    requests.storage: "1Ti"
    pods: "128"
```
→ 防止误操作把集群打挂。

---

## 5 网络与连通性（端口矩阵 + 安全组）

**5.1 Ingress 路由矩阵（外部 → L2）**

| 外部域名 | 路径 | IngressClass | 后端 Service:Port | 协议 | 需证书 |
|---|---|---|---|---|---|
| `aiot.example.com` | `/` | nginx-external | `gateway:8080` | HTTPS (JWT) | 是 |
| `mqtt.aiot.example.com` | `/mqtt` (WebSocket) | nginx-external | `emqx:8083` | WSS | 是 |
| `emqx-dashboard.aiot.example.com` | `/` | nginx-internal (IP 白名单) | `emqx:18083` | HTTPS | 是 |
| `nacos.aiot.example.com` | `/` | nginx-internal | `nacos:8848` | HTTPS | 是 |
| `grafana.aiot.example.com` | `/` | nginx-internal | `grafana:3000` | HTTPS (OIDC) | 是 |
| `prometheus.aiot.example.com` | `/` | nginx-internal | `prometheus:9090` | HTTPS + BasicAuth | 是 |

**5.2 纯 TCP 出口（非 HTTP，走 L4 LoadBalancer）**

| Service:Port | 协议 | 源 | 用途 |
|---|---|---|---|
| `emqx:1883` | TCP | 设备端 IP 白名单 | MQTT v3/v5 设备接入 |
| 可选 `emqx:8883` | TCP TLS | 公网 | MQTT over TLS |

**5.3 内部 Service 到 Service 通信（L2 East-West，ClusterIP）**

| 调用方 | 被调用方 | 端口 | 用途 | 配置注入来源 |
|---|---|---|---|---|
| gateway | nacos | 8848,9848 | 服务发现 | ConfigMap `NACOS_ADDR` |
| 全部业务 | nacos | 同上 | 注册/配置拉取 | 同上 |
| home/device/auth | mysql | 3306 | JDBC | `MYSQL_HOST/PORT` |
| device/rule/shadow/data-parser/home/auth | redis | 6379 | 缓存/影子/令牌 | `REDIS_HOST/PORT` |
| device/auth/mqtt-adapter | emqx | 1883/18083(API) | 指令下发/Webhook | `EMQX_HOST` + `EMQX_API_USER/PWD` |
| device→home/rule/data-parser | 对应 Svc | 8083/8084/8086 | 跨服务 REST | ConfigMap 内 `AIOT_*_URL` |
| mqtt-adapter → data-parser | data-parser:8086 | HTTP | 协议解析转发 | `AIOT_DATA_PARSER_BASE_URL` |

**5.4 L0 安全组入向最小化（云资源层）**

| SG | 入向端口 | 来源 CIDR | 用途 |
|---|---|---|---|
| sg-aiot-public (面向设备/用户) | 443,80 | 0.0.0.0/0 | HTTPS/WSS Ingress |
| sg-aiot-public | 1883,8883 | 0.0.0.0/0 | MQTT (必须设备证书/密码) |
| sg-aiot-internal (运维) | 443,80,22 | 公司出口 IP + VPN | nacos/grafana/prom/jumpbox |
| sg-aiot-db | 3306,6379,8848,9848 | 仅 k8s Node Pod CIDR | K8s → RDS/Redis/Nacos |

---

## 6 命名与标签约定（全栈一致，便于成本与审计）

**6.1 命名规则**
- Namespace: `aiot`（单租户），多环境用 `aiot-dev` `aiot-staging` `aiot-prod` 分集群不共集群
- Workload 名：去掉 `aiot-` 前缀（因为 Namespace 已隔离），即 `gateway device-service home-service ...`
- PVC: `<workload>-data` 例如 `mysql-data` `prometheus-data`
- Secret/ConfigMap: `aiot-secrets` `aiot-common-config` `aiot-service-urls`
- 镜像: `ghcr.io/aidencck/aiot-java/aiot-<svc>:<tag>` 保持与 compose 一致

**6.2 标签（Labels & Selectors，必须打）**
```yaml
labels:
  app: <workload-name>                # 必须，Service Selector 用
  app.kubernetes.io/name: aiot
  app.kubernetes.io/component: <infra|svc|obs>
  app.kubernetes.io/part-of: aiot-platform
  app.kubernetes.io/env: <dev|staging|prod>
  tier: <data|app|edge>               # 用于亲和/反亲和
```

**6.3 Pod 反亲和（高可用门禁）**
- `emqx ×3`, `mysql 只读副本`, `gateway`: `podAntiAffinity requiredDuringSchedulingIgnoredDuringExecution topologyKey: kubernetes.io/hostname`
- 业务服务至少 `preferredDuringScheduling` 打散

---

## 7 配置与密钥分层注入（杜绝镜像包含环境差异）

| 配置类型 | 数据样例 | K8s 对象 | 挂载方式 |
|---|---|---|---|
| 通用固定配置 | TZ, NACOS_GROUP, 跨服务超时 | ConfigMap `aiot-common-config` | envFrom `configMapRef` |
| 内部服务名/端口映射 | MYSQL_HOST=mysql, REDIS_HOST=redis | ConfigMap `aiot-service-urls` | envFrom + 单条 `configMapKeyRef` |
| 大体积应用配置 | prometheus.yml, loki.yml, alertmanager.yml, grafana provisioning, mysql init SQL | 独立 ConfigMap | `volumeMounts` 只读挂载 |
| 密码/令牌/密钥 | MYSQL_PASSWORD, JWT_SECRET, EMQX_API_PWD, 内部 token | Secret `aiot-secrets` (Opaque) + 生产用 SealedSecret/ExternalSecret | `secretKeyRef` 注入 env |
| Ingress TLS 证书 | `*.aiot.example.com` | Secret `tls-aiot-ingress` (kubernetes.io/tls) | Ingress `spec.tls` |

**生产门禁：** `aiot-secrets` 禁止明文入库，强制 `SealedSecret` 或 `ExternalSecret` → 云 KMS / Vault。

---

## 8 存储层级与存储类映射

| PVC | 访问模式 | 建议 StorageClass | 后端云盘 | 最小 IOPS 要求 | 备份策略 |
|---|---|---|---|---|---|
| mysql-data | RWO | `sc-ssd-rwo` | 超高 IO SSD | 5000 IOPS | 每日全量 + binlog 增量 |
| redis-data | RWO | `sc-ssd-rwo` | SSD | 2000 IOPS | AOF/RDB 到 OBS 周期 |
| nacos-data | RWO | `sc-ssd-rwo` | SSD | 1000 IOPS | 配置导出到 CM |
| emqx-data | RWO | `sc-ssd-rwo` | SSD | 2000 IOPS | 导出规则/ACL |
| prometheus-data | RWO | `sc-ssd-rwo` | 高 IO SSD | 5000 IOPS | 15d 保留 + 远程写 Thanos/Mimir |
| grafana-data | RWO | `sc-ssd-rwo` | SSD | 1000 IOPS | Dashboard 导出 JSON |
| loki-data | RWO | `sc-ssd-rwo` | 高吞吐 SSD | 3000 IOPS | S3 后端长期存储（推荐） |
| tempo-data | RWO | `sc-ssd-rwo` | SSD | 2000 IOPS | S3 后端长期存储（推荐） |
| promtail-positions | RWO | `sc-standard` | 标准盘 | - | 无需备份 |

**替代路径（降成本 & 高可用）：**
- Loki/Tempo/Prometheus 直接走 S3 兼容后端，PVC 仅做本地缓存或去 PVC 化。
- MySQL/Redis 用云托管（删除自带 StatefulSet，改 Secret endpoint 指向托管）。

---

## 9 部署顺序 & 验收门禁（拿来即用的 checklist）

**9.1 部署顺序（严格按依赖）**
1. L0：VPC/SG/NODES/托管 RDS/Redis/OSS/Cert
2. L1：K8s 集群、NodePool、StorageClass、IngressNginx、cert-manager、metrics-server、kube-prometheus CRDs（可选）
3. L2：
   ```
   kubectl apply -k k8s/overlays/<env>
   或
   k8s/deploy.sh <env>
   ```
   L2 内部顺序被 Helm/Kustomize + initContainer 保证。

**9.2 验收门禁（必须全部 PASS 才算上线成功）**

| 编号 | 门禁 | 验证命令/指标 | 失败即阻断 |
|---|---|---|---|
| R1 | Namespace & ResourceQuota 存在 | `kubectl get ns aiot; kubectl get quota -n aiot` | 是 |
| R2 | 所有 PVC Bound | `kubectl get pvc -n aiot \| grep -v Bound` 应为空 | 是 |
| R3 | 所有 StatefulSet Ready=Desired | `kubectl get sts -n aiot` | 是 |
| R4 | 所有 Deployment Ready=Desired | `kubectl get deploy -n aiot` | 是 |
| R5 | 每个业务 Pod readiness=200 | `kubectl exec -n aiot deploy/gateway -- curl -f localhost:8080/actuator/health/readiness` 对 8 服务循环 | 是 |
| R6 | HPA 生效 | `kubectl get hpa -n aiot` TARGETS 非 `<unknown>` | 是 |
| R7 | Ingress 所有域名 HTTPS 200 | 用 curl/外部探针 6 个域名 | 是 |
| R8 | MQTT 1883 连通 + 认证拒绝匿名 | `mosquitto_sub -h mqtt.aiot.example.com -p 1883 -u bad -P bad` 必须被拒 | 是 |
| R9 | Prometheus 所有 target UP=目标数 | `/targets` UI | 是 |
| R10 | Loki 能查到最近 10min 业务日志 | Grafana Explore `{app="device-service"}` 有数据 | 是 |
| R11 | 跨服务调用链路 Tempo 可查 | TraceID 能从 gateway → device → rule 全链路 | 是 |
| R12 | Secret 全部非空 | `kubectl get secret aiot-secrets -n aiot -o jsonpath='{.data}'` 关键字段非空 | 是 |
| R13 | 资源使用率 ≤ limits 70% | `kubectl top pods -n aiot` | 否（告警） |

---

## 10 交付文件索引（本仓库内绝对路径）

| 用途 | 文件 |
|---|---|
| 三层总架构与逻辑关系文档（本文） | [AIOT_K8S_RESOURCE_MAP.md](file:///Users/aiden/Projects/AIOT-java/k8s/AIOT_K8S_RESOURCE_MAP.md) |
| Kustomize base 总入口 | [kustomization.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/kustomization.yaml) |
| 命名空间 | [namespace.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/namespace.yaml) |
| 9 个 PVC | [pvcs.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/pvcs.yaml) |
| ConfigMap 两份 + 其他配置 CM | [configmaps.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/configmaps.yaml) |
| Secret 模板 | [secrets.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/secrets.yaml) |
| Ingress 路由 6 域名 | [ingress.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/ingress.yaml) |
| 4 中间件 StatefulSet | [mysql.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/infra/mysql.yaml) [redis.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/infra/redis.yaml) [nacos.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/infra/nacos.yaml) [emqx.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/infra/emqx.yaml) |
| 8 业务 Deployment+Service+HPA | [gateway.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/services/gateway.yaml) [auth-service.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/services/auth-service.yaml) [device-service.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/services/device-service.yaml) [home-service.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/services/home-service.yaml) [rule-engine.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/services/rule-engine.yaml) [shadow-service.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/services/shadow-service.yaml) [mqtt-adapter.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/services/mqtt-adapter.yaml) [data-parser.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/services/data-parser.yaml) |
| 6 可观测工作负载 | [prometheus.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/observability/prometheus.yaml) [alertmanager.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/observability/alertmanager.yaml) [loki.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/observability/loki.yaml) [promtail.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/observability/promtail.yaml) [tempo.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/observability/tempo.yaml) [grafana.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/base/observability/grafana.yaml) |
| Helm Chart 可参数化打包 | [Chart.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/helm/Chart.yaml) [values.yaml](file:///Users/aiden/Projects/AIOT-java/k8s/helm/values.yaml) 及 `helm/templates/*` |
| 环境 Overlay (dev/staging/prod) | [overlays/dev](file:///Users/aiden/Projects/AIOT-java/k8s/overlays/dev) [overlays/staging](file:///Users/aiden/Projects/AIOT-java/k8s/overlays/staging) [overlays/prod](file:///Users/aiden/Projects/AIOT-java/k8s/overlays/prod) |
| 一键部署/卸载 | [deploy.sh](file:///Users/aiden/Projects/AIOT-java/k8s/deploy.sh) [undeploy.sh](file:///Users/aiden/Projects/AIOT-java/k8s/undeploy.sh) |
