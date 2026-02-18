package com.yzdbx.cdc.cdc.db;

/**
 * 目前 MySQL 的写入语法与通用写法一致，单独保留类便于后续针对 MySQL 做 upsert、反引号等增强。
 */
public class MySqlDbWriter extends GenericDbWriter {
    // 复用 GenericDbWriter 的实现
}

