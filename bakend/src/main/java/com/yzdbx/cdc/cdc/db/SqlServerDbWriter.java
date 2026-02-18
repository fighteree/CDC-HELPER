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
 * SQL Server 写入器：
 * - 标识符使用方括号 []，避免关键字冲突
 * - INSERT 自动跳过 AUTOINCREMENT/IDENTITY 列（常见为主键）
 *
 * <p>注意：
 * - 如果未来希望“连 IDENTITY 值也写入目标库”，需要在 insert 前后执行：
 *   SET IDENTITY_INSERT [schema].[table] ON/OFF（并且要求该表一次只能有一个会话开启）
 *   这类策略建议做成可配置开关。
 */
public class SqlServerDbWriter implements DbWriter {

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
                // 例如 IDENTITY 列：让 SQL Server 自动生成
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

        // 形如：INSERT INTO [dbo].[t_user] ([name],[age]) VALUES (?,?)
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
        // 约定：update/delete 依赖“用户勾选的主键字段”拼 where 条件
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
        // 常见策略：自增列（IDENTITY）插入时由目标库生成
        if (Boolean.TRUE.equals(cm.getAutoIncrement())) {
            return true;
        }
        // 也可根据类型名包含 identity 做兜底
        if (cm.getType() != null && cm.getType().toLowerCase().contains("identity")) {
            return true;
        }
        return false;
    }

    private String tableRef(String schema, String table) {
        // SQL Server schema.table，且两段都需要用 [] 包裹避免关键字冲突
        if (StringUtils.hasText(schema)) {
            return wrap(schema) + "." + wrap(table);
        }
        return wrap(table);
    }

    private String wrap(String name) {
        return "[" + name + "]";
    }
}

