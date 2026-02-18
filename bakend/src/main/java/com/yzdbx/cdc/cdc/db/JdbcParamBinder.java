package com.yzdbx.cdc.cdc.db;

import com.yzdbx.cdc.service.DataSourceService;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * JDBC 参数绑定工具：
 * <p>
 * Debezium 事件里的值类型并不总能直接塞进目标库列里（尤其是时间字段经常是毫秒时间戳）。
 * 这里根据“目标列的 JDBC 类型”做必要转换，避免出现诸如：
 * - Data truncation: Incorrect datetime value: '1771373749000'
 * <p>
 * 约定：
 * - 如果 columnMeta/type 取不到，则退化为 ps.setObject
 * - 转换失败也退化为 ps.setObject（尽量不中断同步）
 */
public final class JdbcParamBinder {

    private JdbcParamBinder() {
    }

    /**
     * 根据目标列类型做适当转换，尤其是 datetime/timestamp/date/time。
     * <p>
     * 支持的输入：
     * - Number: 认为是 epoch millis
     * - 纯数字字符串: 认为是 epoch millis
     * - 其它字符串: 尝试用 Timestamp.valueOf/Date.valueOf/Time.valueOf 解析
     */
    public static void setTypedParameter(PreparedStatement ps,
                                         int index,
                                         Object value,
                                         DataSourceService.ColumnMeta columnMeta) throws SQLException {
        if (value == null || columnMeta == null || columnMeta.getType() == null) {
            ps.setObject(index, value);
            return;
        }
        String jdbcType = columnMeta.getType();
        String type = jdbcType.toLowerCase();
        try {
            if (type.contains("datetime") || type.contains("timestamp")) {
                if (value instanceof Number) {
                    ps.setTimestamp(index, new Timestamp(((Number) value).longValue()));
                } else {
                    String s = value.toString();
                    if (s.matches("^\\d+$")) {
                        ps.setTimestamp(index, new Timestamp(Long.parseLong(s)));
                    } else {
                        ps.setTimestamp(index, Timestamp.valueOf(s));
                    }
                }
                return;
            }
            if ("date".equals(type)) {
                if (value instanceof Number) {
                    ps.setDate(index, new Date(((Number) value).longValue()));
                } else {
                    String s = value.toString();
                    if (s.matches("^\\d+$")) {
                        ps.setDate(index, new Date(Long.parseLong(s)));
                    } else {
                        ps.setDate(index, Date.valueOf(s));
                    }
                }
                return;
            }
            if (type.contains("time")) {
                if (value instanceof Number) {
                    ps.setTime(index, new Time(((Number) value).longValue()));
                } else {
                    String s = value.toString();
                    if (s.matches("^\\d+$")) {
                        ps.setTime(index, new Time(Long.parseLong(s)));
                    } else {
                        ps.setTime(index, Time.valueOf(s));
                    }
                }
                return;
            }
        } catch (Exception ignore) {
            // 解析/转换失败：交给 JDBC 驱动自行处理或报错（这里选择尽量继续）
        }
        ps.setObject(index, value);
    }
}

