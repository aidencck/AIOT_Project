#!/bin/bash
set -e
# MySQL 官方镜像会 source 此脚本，此时 MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD 环境变量已可用。
# 使用 shell 变量展开，避免 .sql 中 ${MYSQL_PASSWORD} 被当作字面量导致密码错配。
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
CREATE DATABASE IF NOT EXISTS aiot_cloud DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS aiot_home  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'aiot_app'@'%' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
GRANT ALL PRIVILEGES ON aiot_cloud.* TO 'aiot_app'@'%';
GRANT ALL PRIVILEGES ON aiot_home.*  TO 'aiot_app'@'%';
FLUSH PRIVILEGES;
EOSQL
