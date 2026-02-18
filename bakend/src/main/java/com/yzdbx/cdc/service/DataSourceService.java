package com.yzdbx.cdc.service;

import com.yzdbx.cdc.mapper.DataSourceMapper;
import com.yzdbx.cdc.model.entity.DataSourceEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据源服务：
 * <p>
 * 主要做两件事：
 * - 管理平台的“数据源配置”CRUD（持久化到 cdc_admin 管理库）
 * - 使用 JDBC 元数据能力探测真实数据库的表/字段信息（用于映射配置界面下拉选择）
 */
@Service
public class DataSourceService {

    private final DataSourceMapper dataSourceMapper;

    public DataSourceService(DataSourceMapper dataSourceMapper) {
        this.dataSourceMapper = dataSourceMapper;
    }

    /** 分页列表，返回 { list, total } */
    public Map<String, Object> list(String keyword, String dbType, Integer status, int pageNum, int pageSize) {
        if (!StringUtils.hasText(keyword)) keyword = null;
        if (!StringUtils.hasText(dbType)) dbType = null;
        if (pageNum < 1) pageNum = 1;
        if (pageSize <= 0 || pageSize > 500) pageSize = 20;
        int offset = (pageNum - 1) * pageSize;

        int total = dataSourceMapper.countByCondition(keyword, dbType, status);
        List<DataSourceEntity> list = dataSourceMapper.selectAll(keyword, dbType, status, offset, pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return result;
    }

    public DataSourceEntity getById(Long id) {
        return dataSourceMapper.selectById(id);
    }

    public Long create(DataSourceEntity entity) {
        // 如果前端没填 jdbcUrl，则按 dbType/host/port/dbName 生成默认 URL
        if (!StringUtils.hasText(entity.getJdbcUrl())) {
            entity.setJdbcUrl(buildJdbcUrl(entity));
        }
        dataSourceMapper.insert(entity);
        return entity.getId();
    }

    public void update(DataSourceEntity entity) {
        if (!StringUtils.hasText(entity.getJdbcUrl())) {
            entity.setJdbcUrl(buildJdbcUrl(entity));
        }
        dataSourceMapper.update(entity);
    }

    public void delete(Long id) {
        dataSourceMapper.deleteById(id);
    }

    public boolean testConnection(Long id) {
        DataSourceEntity ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在，id=" + id);
        }
        return testConnectionInternal(ds);
    }

    public boolean testConnectionInternal(DataSourceEntity ds) {
        String dbType = ds.getDbType() == null ? "" : ds.getDbType().toLowerCase();
        if ("mongodb".equals(dbType)) {
            try (MongoClient client = MongoClients.create(buildMongoConnectionString(ds))) {
                client.getDatabase(ds.getDbName() != null ? ds.getDbName() : "admin").runCommand(new Document("ping", 1));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        String jdbcUrl = StringUtils.hasText(ds.getJdbcUrl()) ? ds.getJdbcUrl() : buildJdbcUrl(ds);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, ds.getUsername(), ds.getPassword())) {
            return conn.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    public List<TableMeta> listTables(Long dataSourceId, String schema) {
        DataSourceEntity ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在，id=" + dataSourceId);
        }
        String dbType = ds.getDbType() == null ? "" : ds.getDbType().toLowerCase();
        if ("mongodb".equals(dbType)) {
            return listMongoCollections(ds);
        }
        String jdbcUrl = StringUtils.hasText(ds.getJdbcUrl()) ? ds.getJdbcUrl() : buildJdbcUrl(ds);
        List<TableMeta> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData meta = conn.getMetaData();
            if ("mysql".equals(dbType) || "mariadb".equals(dbType)) {
                // MySQL: 使用 catalog = dbName，schemaPattern 置空，从而只返回当前库的表
                try (ResultSet rs = meta.getTables(ds.getDbName(), null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        // 再保险：只接收 TABLE_CAT 等于当前库的记录
                        String catalog = rs.getString("TABLE_CAT");
                        if (!ds.getDbName().equals(catalog)) {
                            continue;
                        }
                        TableMeta tm = new TableMeta();
                        tm.setSchema(catalog);
                        tm.setName(rs.getString("TABLE_NAME"));
                        tm.setComment(rs.getString("REMARKS"));
                        result.add(tm);
                    }
                }
            } else {
                // 非 MySQL：schemaPattern 允许前端按 schema 过滤（如 SQL Server 的 dbo）
                String schemaPattern = StringUtils.hasText(schema) ? schema : null;
                try (ResultSet rs = meta.getTables(null, schemaPattern, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        TableMeta tm = new TableMeta();
                        tm.setSchema(rs.getString("TABLE_SCHEM"));
                        tm.setName(rs.getString("TABLE_NAME"));
                        tm.setComment(rs.getString("REMARKS"));
                        result.add(tm);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取表信息失败: " + e.getMessage(), e);
        }
        return result;
    }

    public List<ColumnMeta> listColumns(Long dataSourceId, String schema, String tableName) {
        DataSourceEntity ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在，id=" + dataSourceId);
        }
        String dbType = ds.getDbType() == null ? "" : ds.getDbType().toLowerCase();
        if ("mongodb".equals(dbType)) {
            return listMongoCollectionFields(ds, tableName);
        }
        String jdbcUrl = StringUtils.hasText(ds.getJdbcUrl()) ? ds.getJdbcUrl() : buildJdbcUrl(ds);
        List<ColumnMeta> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, ds.getUsername(), ds.getPassword())) {
            DatabaseMetaData meta = conn.getMetaData();
            if ("mysql".equals(dbType) || "mariadb".equals(dbType)) {
                // MySQL: 使用 catalog = dbName，schemaPattern 置空，只取当前库下该表的字段
                try (ResultSet rs = meta.getColumns(ds.getDbName(), null, tableName, "%")) {
                    while (rs.next()) {
                        ColumnMeta cm = new ColumnMeta();
                        cm.setName(rs.getString("COLUMN_NAME"));
                        cm.setType(rs.getString("TYPE_NAME"));
                        cm.setComment(rs.getString("REMARKS"));
                        cm.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                        // 给 SQL Server 写入器用：判断是否 IDENTITY/自增列
                        cm.setAutoIncrement("YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")));
                        result.add(cm);
                    }
                }
            } else {
                String schemaPattern = StringUtils.hasText(schema) ? schema : null;
                try (ResultSet rs = meta.getColumns(null, schemaPattern, tableName, "%")) {
                    while (rs.next()) {
                        ColumnMeta cm = new ColumnMeta();
                        cm.setName(rs.getString("COLUMN_NAME"));
                        cm.setType(rs.getString("TYPE_NAME"));
                        cm.setComment(rs.getString("REMARKS"));
                        cm.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                        cm.setAutoIncrement("YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")));
                        result.add(cm);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取字段信息失败: " + e.getMessage(), e);
        }
        return result;
    }

    private String buildJdbcUrl(DataSourceEntity ds) {
        String dbType = ds.getDbType() == null ? "" : ds.getDbType().toLowerCase();
        switch (dbType) {
            case "mysql":
            case "mariadb":
                return "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort()
                        + "/" + ds.getDbName() + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
            case "postgresql":
            case "postgres":
                return "jdbc:postgresql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
            case "oracle":
                return "jdbc:oracle:thin:@" + ds.getHost() + ":" + ds.getPort() + ":" + ds.getDbName();
            case "sqlserver":
            case "mssql":
                return "jdbc:sqlserver://" + ds.getHost() + ":" + ds.getPort()
                        + ";databaseName=" + ds.getDbName()+";trustServerCertificate=true";
            case "db2":
                return "jdbc:db2://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
            case "mongodb":
                return buildMongoConnectionString(ds);
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + ds.getDbType());
        }
    }

    private String buildMongoConnectionString(DataSourceEntity ds) {
        String user = ds.getUsername();
        String password = ds.getPassword();
        String host = ds.getHost();
        int port = ds.getPort() != null ? ds.getPort() : 27017;
        String dbName = ds.getDbName() != null ? ds.getDbName() : "admin";
        StringBuilder sb = new StringBuilder("mongodb://");
        if (user != null && !user.isEmpty()) {
            sb.append(urlEncode(user));
            if (password != null && !password.isEmpty()) {
                sb.append(':').append(urlEncode(password));
            }
            sb.append('@');
        }
        sb.append(host).append(':').append(port);
        if (dbName != null && !dbName.isEmpty()) {
            sb.append('/').append(dbName);
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    private List<TableMeta> listMongoCollections(DataSourceEntity ds) {
        List<TableMeta> result = new ArrayList<>();
        try (MongoClient client = MongoClients.create(buildMongoConnectionString(ds))) {
            String dbName = ds.getDbName() != null ? ds.getDbName() : "admin";
            MongoDatabase db = client.getDatabase(dbName);
            for (String name : db.listCollectionNames()) {
                TableMeta tm = new TableMeta();
                tm.setSchema(dbName);
                tm.setName(name);
                result.add(tm);
            }
        } catch (Exception e) {
            throw new RuntimeException("获取 MongoDB 集合列表失败: " + e.getMessage(), e);
        }
        return result;
    }

    private List<ColumnMeta> listMongoCollectionFields(DataSourceEntity ds, String collectionName) {
        List<ColumnMeta> result = new ArrayList<>();
        try (MongoClient client = MongoClients.create(buildMongoConnectionString(ds))) {
            String dbName = ds.getDbName() != null ? ds.getDbName() : "admin";
            MongoCollection<Document> coll = client.getDatabase(dbName).getCollection(collectionName);
            Document first = coll.find().first();
            if (first != null) {
                Set<String> keys = new LinkedHashSet<>(first.keySet());
                int pos = 0;
                for (String key : keys) {
                    ColumnMeta cm = new ColumnMeta();
                    cm.setName(key);
                    cm.setType("document");
                    cm.setOrdinalPosition(++pos);
                    result.add(cm);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取 MongoDB 集合字段失败: " + e.getMessage(), e);
        }
        return result;
    }

    public static class TableMeta {
        private String schema;
        private String name;
        private String comment;

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }

    public static class ColumnMeta {
        private String name;
        private String type;
        private String comment;
        private Integer ordinalPosition;
        private Boolean autoIncrement;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public Integer getOrdinalPosition() {
            return ordinalPosition;
        }

        public void setOrdinalPosition(Integer ordinalPosition) {
            this.ordinalPosition = ordinalPosition;
        }

        public Boolean getAutoIncrement() {
            return autoIncrement;
        }

        public void setAutoIncrement(Boolean autoIncrement) {
            this.autoIncrement = autoIncrement;
        }
    }
}

