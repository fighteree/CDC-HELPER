package com.yzdbx.cdc.cdc.db;

import com.yzdbx.cdc.model.entity.DataSourceEntity;
import com.yzdbx.cdc.model.entity.MappingEntity;
import com.yzdbx.cdc.model.entity.MappingFieldEntity;
import com.yzdbx.cdc.service.DataSourceService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用 JDBC 写入器：使用最基础的 INSERT/UPDATE/DELETE 语法，不做特殊方言处理。
 * 后续可逐个数据库类型替换为更精细的 writer。
 */
public class GenericDbWriter implements DbWriter {

    @Override
    public void insert(Connection conn,
                       DataSourceEntity targetDs,
                       MappingEntity mapping,
                       List<MappingFieldEntity> fields,
                       Map<String, Object> targetRow,
                       Map<String, DataSourceService.ColumnMeta> columnMetaMap) throws Exception {
        String table = mapping.getTargetTable();
        StringBuilder cols = new StringBuilder();
        StringBuilder q = new StringBuilder();
        List<MappingFieldEntity> effective = new ArrayList<>();
        for (MappingFieldEntity f : fields) {
            if (cols.length() > 0) {
                cols.append(",");
                q.append(",");
            }
            cols.append(f.getTargetCol());
            q.append("?");
            effective.add(f);
        }
        String sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + q + ")";
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
        String table = mapping.getTargetTable();
        StringBuilder setPart = new StringBuilder();
        StringBuilder wherePart = new StringBuilder();
        for (MappingFieldEntity f : fields) {
            if (f.getIsPk() != null && f.getIsPk() == 1) {
                if (wherePart.length() > 0) wherePart.append(" AND ");
                wherePart.append(f.getTargetCol()).append(" = ?");
            } else {
                if (setPart.length() > 0) setPart.append(", ");
                setPart.append(f.getTargetCol()).append(" = ?");
            }
        }
        String sql = "UPDATE " + table + " SET " + setPart + " WHERE " + wherePart;
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
        String table = mapping.getTargetTable();
        StringBuilder wherePart = new StringBuilder();
        for (MappingFieldEntity f : fields) {
            if (f.getIsPk() != null && f.getIsPk() == 1) {
                if (wherePart.length() > 0) wherePart.append(" AND ");
                wherePart.append(f.getTargetCol()).append(" = ?");
            }
        }
        String sql = "DELETE FROM " + table + " WHERE " + wherePart;
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
}

