package com.yzdbx.cdc.cdc.db;

import com.yzdbx.cdc.model.entity.DataSourceEntity;
import com.yzdbx.cdc.model.entity.MappingEntity;
import com.yzdbx.cdc.model.entity.MappingFieldEntity;
import com.yzdbx.cdc.service.DataSourceService;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Oracle 写入器：
 * - 标识符使用双引号包裹，避免关键字冲突
 * - INSERT 自动跳过自增列（Oracle 12c+ 的 IDENTITY 列或序列）
 * - 支持 schema.table 格式
 */
public class OracleDbWriter implements DbWriter {

    @Override
    public void insert(Connection conn,
                       DataSourceEntity targetDs,
                       MappingEntity mapping,
                       List<MappingFieldEntity> fields,
                       Map<String, Object> targetRow,
                       Map<String, DataSourceService.ColumnMeta> columnMetaMap) throws Exception {
        String tableRef = tableRef(mapping.getTargetSchema(), mapping.getTargetTable());

        StringBuilder cols = new StringBuilder();
        StringBuilder q = new StringBuilder();
        List<MappingFieldEntity> effective = new ArrayList<>();

        for (MappingFieldEntity f : fields) {
            DataSourceService.ColumnMeta cm = columnMetaMap.get(f.getTargetCol());
            if (shouldSkipOnInsert(f, cm)) {
                // 自增列：让 Oracle 自动生成（通过序列或 IDENTITY）
                continue;
            }
            if (cols.length() > 0) {
                cols.append(", ");
                q.append(", ");
            }
            cols.append(wrap(f.getTargetCol()));
            q.append("?");
            effective.add(f);
        }

        // 形如：INSERT INTO "SCHEMA"."T_USER" ("NAME","AGE") VALUES (?,?)
        String sql = "INSERT INTO " + tableRef + " (" + cols + ") VALUES (" + q + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (MappingFieldEntity f : effective) {
                Object val = targetRow.get(f.getTargetCol());
                JdbcParamBinder.setTypedParameter(ps, idx++, val, columnMetaMap.get(f.getTargetCol()));
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Connection conn,
                       DataSourceEntity targetDs,
                       MappingEntity mapping,
                       List<MappingFieldEntity> fields,
                       Map<String, Object> targetRow,
                       Map<String, DataSourceService.ColumnMeta> columnMetaMap) throws Exception {
        String tableRef = tableRef(mapping.getTargetSchema(), mapping.getTargetTable());

        StringBuilder setPart = new StringBuilder();
        StringBuilder wherePart = new StringBuilder();
        for (MappingFieldEntity f : fields) {
            if (f.getIsPk() != null && f.getIsPk() == 1) {
                if (wherePart.length() > 0) wherePart.append(" AND ");
                wherePart.append(wrap(f.getTargetCol())).append(" = ?");
            } else {
                if (setPart.length() > 0) setPart.append(", ");
                setPart.append(wrap(f.getTargetCol())).append(" = ?");
            }
        }
        String sql = "UPDATE " + tableRef + " SET " + setPart + " WHERE " + wherePart;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (MappingFieldEntity f : fields) {
                if (f.getIsPk() == null || f.getIsPk() != 1) {
                    Object val = targetRow.get(f.getTargetCol());
                    JdbcParamBinder.setTypedParameter(ps, idx++, val, columnMetaMap.get(f.getTargetCol()));
                }
            }
            for (MappingFieldEntity f : fields) {
                if (f.getIsPk() != null && f.getIsPk() == 1) {
                    Object val = targetRow.get(f.getTargetCol());
                    JdbcParamBinder.setTypedParameter(ps, idx++, val, columnMetaMap.get(f.getTargetCol()));
                }
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Connection conn,
                       DataSourceEntity targetDs,
                       MappingEntity mapping,
                       List<MappingFieldEntity> fields,
                       Map<String, Object> targetRow,
                       Map<String, DataSourceService.ColumnMeta> columnMetaMap) throws Exception {
        String tableRef = tableRef(mapping.getTargetSchema(), mapping.getTargetTable());

        StringBuilder wherePart = new StringBuilder();
        for (MappingFieldEntity f : fields) {
            if (f.getIsPk() != null && f.getIsPk() == 1) {
                if (wherePart.length() > 0) wherePart.append(" AND ");
                wherePart.append(wrap(f.getTargetCol())).append(" = ?");
            }
        }
        String sql = "DELETE FROM " + tableRef + " WHERE " + wherePart;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (MappingFieldEntity f : fields) {
                if (f.getIsPk() != null && f.getIsPk() == 1) {
                    Object val = targetRow.get(f.getTargetCol());
                    JdbcParamBinder.setTypedParameter(ps, idx++, val, columnMetaMap.get(f.getTargetCol()));
                }
            }
            ps.executeUpdate();
        }
    }

    private boolean shouldSkipOnInsert(MappingFieldEntity f, DataSourceService.ColumnMeta cm) {
        if (cm == null) return false;
        // Oracle 12c+ IDENTITY 列
        if (Boolean.TRUE.equals(cm.getAutoIncrement())) {
            return true;
        }
        // 根据类型名判断是否为序列（常见命名：*_SEQ）
        if (cm.getType() != null && cm.getType().toLowerCase().contains("identity")) {
            return true;
        }
        return false;
    }

    private String tableRef(String schema, String table) {
        // Oracle schema.table，使用双引号包裹（Oracle 标识符大小写敏感）
        if (StringUtils.hasText(schema)) {
            return wrap(schema) + "." + wrap(table);
        }
        return wrap(table);
    }

    private String wrap(String name) {
        // Oracle 使用双引号包裹标识符
        return "\"" + name.toUpperCase() + "\"";
    }
}
