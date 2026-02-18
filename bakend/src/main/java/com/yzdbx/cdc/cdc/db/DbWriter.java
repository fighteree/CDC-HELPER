package com.yzdbx.cdc.cdc.db;

import com.yzdbx.cdc.model.entity.DataSourceEntity;
import com.yzdbx.cdc.model.entity.MappingEntity;
import com.yzdbx.cdc.model.entity.MappingFieldEntity;
import com.yzdbx.cdc.service.DataSourceService;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * 目标库写入抽象（DB → DB 同步的落库层）。
 * <p>
 * 为什么需要它：
 * - 不同数据库的 SQL 方言差异很大（标识符转义、schema 引用、insert/update 语法、主键/自增策略等）
 * - 同一份 CDC 事件在不同目标库需要不同的“拼 SQL + 绑定参数 + 类型转换”策略
 * <p>
 * 入参约定：
 * - mapping/fields: 用户在平台配置的映射规则（决定写哪张表、列如何对应、哪些列是主键）
 * - targetRow: 已按字段映射/表达式求值后的目标行数据（key=目标列名）
 * - columnMetaMap: 目标表列元信息（用于类型转换、IDENTITY/自增处理等）
 */
public interface DbWriter {

    void insert(Connection conn,
                DataSourceEntity targetDs,
                MappingEntity mapping,
                List<MappingFieldEntity> fields,
                Map<String, Object> targetRow,
                Map<String, DataSourceService.ColumnMeta> columnMetaMap) throws Exception;

    void update(Connection conn,
                DataSourceEntity targetDs,
                MappingEntity mapping,
                List<MappingFieldEntity> fields,
                Map<String, Object> targetRow,
                Map<String, DataSourceService.ColumnMeta> columnMetaMap) throws Exception;

    void delete(Connection conn,
                DataSourceEntity targetDs,
                MappingEntity mapping,
                List<MappingFieldEntity> fields,
                Map<String, Object> targetRow,
                Map<String, DataSourceService.ColumnMeta> columnMetaMap) throws Exception;
}

