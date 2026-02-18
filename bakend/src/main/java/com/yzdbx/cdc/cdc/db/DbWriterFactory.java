package com.yzdbx.cdc.cdc.db;

import com.yzdbx.cdc.model.entity.DataSourceEntity;
import org.springframework.stereotype.Component;

/**
 * 根据目标数据源类型选择合适的 DbWriter 实现。
 * <p>
 * - mysql/sqlserver/oracle: 使用专用 writer 处理方言细节
 * - 其它类型：使用 GenericDbWriter 保底跑通
 */
@Component
public class DbWriterFactory {

    // writer 通常无状态（仅拼 SQL/绑定参数），因此可以作为单例复用
    private final DbWriter mySqlWriter = new MySqlDbWriter();
    private final DbWriter sqlServerWriter = new SqlServerDbWriter();
    private final DbWriter oracleWriter = new OracleDbWriter();
    private final DbWriter genericWriter = new GenericDbWriter();

    public DbWriter getWriter(DataSourceEntity targetDs) {
        String dbType = targetDs.getDbType() == null ? "" : targetDs.getDbType().toLowerCase();
        switch (dbType) {
            case "mysql":
            case "mariadb":
                return mySqlWriter;
            case "sqlserver":
            case "mssql":
                return sqlServerWriter;
            case "oracle":
                return oracleWriter;
            default:
                return genericWriter;
        }
    }
}

