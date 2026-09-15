ALTER TABLE `ai_mysql_write_outbox`
    ADD COLUMN `dead_letter` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '死信标记' AFTER `retry_count`;
