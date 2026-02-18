/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80019
 Source Host           : localhost:3306
 Source Schema         : cdc_admin

 Target Server Type    : MySQL
 Target Server Version : 80019
 File Encoding         : 65001

 Date: 18/02/2026 21:17:56
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for cdc_change_log
-- ----------------------------
DROP TABLE IF EXISTS `cdc_change_log`;
CREATE TABLE `cdc_change_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mapping_id` bigint NOT NULL,
  `source_table` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'INSERT/UPDATE/DELETE',
  `target_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DB/HTTP/MQ',
  `success` tinyint NOT NULL COMMENT '1成功 0失败',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `raw_data_json` json NOT NULL COMMENT 'Debezium 事件原始数据或精简版',
  `cost_ms` int NULL DEFAULT NULL COMMENT '处理耗时',
  `replay_count` int NOT NULL DEFAULT 0 COMMENT '重放次数',
  `last_replay_at` datetime NULL DEFAULT NULL COMMENT '最后一次重放时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for cdc_data_source
-- ----------------------------
DROP TABLE IF EXISTS `cdc_data_source`;
CREATE TABLE `cdc_data_source`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据源名称',
  `db_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'mysql/oracle/sqlserver等',
  `host` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `port` int NOT NULL,
  `db_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `jdbc_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '建议加密存储',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CDC 平台数据源配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for cdc_http_target
-- ----------------------------
DROP TABLE IF EXISTS `cdc_http_target`;
CREATE TABLE `cdc_http_target`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mapping_id` bigint NOT NULL,
  `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'POST',
  `headers_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '请求头JSON',
  `timeout_ms` int NOT NULL DEFAULT 5000,
  `retry_times` int NOT NULL DEFAULT 3,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_http_target_mapping`(`mapping_id` ASC) USING BTREE,
  CONSTRAINT `fk_http_target_mapping` FOREIGN KEY (`mapping_id`) REFERENCES `cdc_mapping` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'HTTP接口目标配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for cdc_mapping
-- ----------------------------
DROP TABLE IF EXISTS `cdc_mapping`;
CREATE TABLE `cdc_mapping`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '映射名称',
  `source_data_source_id` bigint NOT NULL,
  `source_schema` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `source_table` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_kafka_servers` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Debezium Kafka 地址，例如 host1:9092,host2:9092',
  `target_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DB/HTTP/MQ',
  `target_data_source_id` bigint NULL DEFAULT NULL COMMENT 'DB to DB 时使用',
  `target_schema` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `target_table` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_mapping_source_ds`(`source_data_source_id` ASC) USING BTREE,
  CONSTRAINT `fk_mapping_source_ds` FOREIGN KEY (`source_data_source_id`) REFERENCES `cdc_data_source` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CDC 映射配置主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for cdc_mapping_field
-- ----------------------------
DROP TABLE IF EXISTS `cdc_mapping_field`;
CREATE TABLE `cdc_mapping_field`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mapping_id` bigint NOT NULL,
  `source_col` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_col` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_pk` tinyint NOT NULL DEFAULT 0 COMMENT '是否主键',
  `expr` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '转换表达式，如 ${source.table.col} 或函数表达式',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `result_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '结果类型：AUTO/STRING/INT/DECIMAL/BOOLEAN/DATETIME',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_mapping_field_mapping`(`mapping_id` ASC) USING BTREE,
  CONSTRAINT `fk_mapping_field_mapping` FOREIGN KEY (`mapping_id`) REFERENCES `cdc_mapping` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字段级映射配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for cdc_mq_target
-- ----------------------------
DROP TABLE IF EXISTS `cdc_mq_target`;
CREATE TABLE `cdc_mq_target`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mapping_id` bigint NOT NULL,
  `mq_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'RABBITMQ/KAFKA/ROCKETMQ',
  `server_addr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `topic` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'topic 或 exchange',
  `routing_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'RabbitMQ/RocketMQ 可用',
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `extra_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'JSON 格式扩展配置',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_mq_target_mapping`(`mapping_id` ASC) USING BTREE,
  CONSTRAINT `fk_mq_target_mapping` FOREIGN KEY (`mapping_id`) REFERENCES `cdc_mapping` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MQ 目标配置' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
