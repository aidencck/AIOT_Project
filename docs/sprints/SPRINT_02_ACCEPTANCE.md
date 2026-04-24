# Sprint 2 需求定义与验收单

## 功能模块：设备拓扑与配网域 (Device Domain)

### 1. 业务背景
建立产品(Product)、设备(Device)与网关的拓扑关联，支持极简配网流程，并在云端和边缘端维持设备的“影子状态”。

### 2. 验收标准 (Acceptance Criteria)

#### 2.1 产品与物模型 (Product)
*   **[AC-1.1]**: 当创建产品时，必须定义节点类型（直连、网关、子设备），后台自动生成 8 位 `productKey`。
*   **[AC-1.2]**: [架构师待重构] `thingModelJson` 必须在保存前经过 JSON Schema 强校验，并绑定唯一版本号。

#### 2.2 设备拓扑与管理 (Device Topology)
*   **[AC-2.1]**: 当创建一个节点类型为 `3 (子设备)` 的设备时，必须传入 `gatewayId` 并且校验其父节点必须是一个类型为 `2 (网关)` 的有效设备。
*   **[AC-2.2]**: [产品 VP 待重构] 必须校验同一 `productKey` 下的 `deviceName` 全局唯一，避免重名冲突。
*   **[AC-2.3]**: 当删除网关设备时，必须将其名下的所有子设备解绑（或级联删除），并同步清理对应的 DeviceCredential 凭证。

#### 2.4 配网机制 (Provisioning)
*   **[AC-4.1]**: 云端生成有效期为 10 分钟的临时 Token 存入 Redis，并与目标 `homeId` 绑定。
*   **[AC-4.2]**: [安全增强待重构] 当设备使用该 Token 换取 `deviceSecret` 和 MQTT 接入点后，必须将 `deviceSecret` 以 Hash 形式加密存入 DB，防止被拖库泄漏。

#### 2.5 影子服务 (Device Shadow)
*   **[AC-5.1]**: 提供针对单个设备的 `reported`（上报状态）和 `desired`（期望状态）的独立读写接口。
*   **[AC-5.2]**: [业务闭环待重构] 当应用层更新 `desired` 时，必须计算与 `reported` 的 Delta 差异，并通过 MQTT 或事件总线异步下发指令给真实物理设备。

### 3. 验证结果 (Verification / Sign-off)

*   [x] `[AC-1.1]` - 已实现 (`ProductServiceImpl.java`)
*   [ ] `[AC-1.2]` - [VP 反馈：物模型强校验待实现] (Issue #37)
*   [x] `[AC-2.1]` - 已实现
*   [ ] `[AC-2.2]` - [VP 反馈：生命周期与重名校验缺陷待修复] (Issue #38)
*   [x] `[AC-2.3]` - 已实现
*   [x] `[AC-4.1]` - 已实现 (`ProvisionServiceImpl.java`)
*   [ ] `[AC-4.2]` - [VP 反馈：凭证防泄漏与一型一密待实现] (Issue #39)
*   [x] `[AC-5.1]` - 已实现
*   [ ] `[AC-5.2]` - [VP 反馈：影子 Delta 差异计算与指令下发断层] (Issue #40)
