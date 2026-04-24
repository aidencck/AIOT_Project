# 数据库表关联关系

本文档基于以下建表脚本整理当前项目数据库表之间的逻辑关联关系：

- `aiot-home-service/src/main/resources/schema.sql`
- `aiot-device-service/src/main/resources/schema.sql`

说明：当前 DDL 主要使用逻辑外键（字段关联），未显式声明 `FOREIGN KEY` 约束。

## ER 图（Mermaid）

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {
  'primaryColor': '#0f172a',
  'primaryTextColor': '#e2e8f0',
  'primaryBorderColor': '#38bdf8',
  'lineColor': '#64748b',
  'secondaryColor': '#111827',
  'tertiaryColor': '#0b1220',
  'background': '#020617'
}}}%%
erDiagram
  "user_info" {
    varchar "id PK"
    varchar "phone UK"
    varchar "password"
    varchar "nickname"
  }

  "home_info" {
    varchar "id PK"
    varchar "name"
    varchar "location"
  }

  "room_info" {
    varchar "id PK"
    varchar "home_id IDX"
    varchar "name"
  }

  "home_member" {
    varchar "id PK"
    varchar "home_id UK_PART"
    varchar "user_id UK_PART"
    tinyint "role"
  }

  "product_info" {
    varchar "id PK"
    varchar "product_key UK"
    varchar "name"
    tinyint "node_type"
  }

  "device_info" {
    varchar "id PK"
    varchar "device_name"
    varchar "product_key IDX"
    varchar "home_id IDX"
    varchar "room_id"
    varchar "gateway_id IDX"
    tinyint "status"
  }

  "device_credential" {
    varchar "id PK"
    varchar "device_id UK"
    tinyint "auth_type"
    varchar "device_secret"
  }

  "home_info" ||--o{ "room_info" : "home_id -> id"
  "home_info" ||--o{ "home_member" : "home_id -> id"
  "user_info" ||--o{ "home_member" : "user_id -> id"
  "product_info" ||--o{ "device_info" : "product_key -> product_key"
  "home_info" ||--o{ "device_info" : "home_id -> id"
  "room_info" ||--o{ "device_info" : "room_id -> id"
  "device_info" ||--|| "device_credential" : "device_id -> id"
  "device_info" ||--o{ "device_info" : "gateway_id -> id"
```
