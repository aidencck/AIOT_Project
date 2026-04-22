# Admin Console API（前端对接）

面向后台管理前端的聚合接口，统一返回家庭、成员、产品、设备、OTA 与运维闭环核心数据。

## 1) 总览接口
- `GET /api/v1/admin-console/overview`
- Header: `Authorization: Bearer <token>`
- Query: `homeId`（可选，不传默认使用当前用户首个家庭）

返回示例字段：
- `homeCount`
- `memberCount`
- `productCount`
- `deviceCount`
- `otaTaskCount`
- `todayAlarmCount`
- `pendingWorkOrderCount`
- `oneTimeResolveRate`

## 2) 最新闭环数据接口
- `GET /api/v1/admin-console/latest-closure`
- Header: `Authorization: Bearer <token>`
- Query: `homeId`（可选）

返回示例字段：
- `homes`
- `members`
- `products`
- `devices`
- `otaTasks`
- `opsOverview`

## 3) 说明
- 前端建议先调用 `/overview` 渲染首页指标，再按需调用 `/latest-closure` 填充列表页。
- 若用户未绑定家庭，接口会返回业务错误提示。
