package com.yzdbx.cdc.cdc;

import com.yzdbx.cdc.mapper.DataSourceMapper;
import com.yzdbx.cdc.mapper.MappingMapper;
import com.yzdbx.cdc.mapper.ChangeLogMapper;
import com.yzdbx.cdc.model.entity.ChangeLogEntity;
import com.yzdbx.cdc.model.entity.DataSourceEntity;
import com.yzdbx.cdc.model.entity.MappingEntity;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基于 Debezium 嵌入式引擎的 CDC 管理器：
 * - 对于 enabled=1 的映射，按其源数据源和源表启动 Debezium 引擎捕获变更
 * - 引擎回调统一交给 CdcRoutingService 处理（DB/HTTP/MQ 路由 + 日志）
 *
 * <p>设计取舍（第一版）：
 * - 每条启用的 mapping 启动一个独立引擎线程：实现简单，隔离性好；后续可按“数据源维度”合并引擎优化资源占用
 * - offset 与 schema history 都落本地文件：避免依赖 Kafka；适合单机/小规模部署
 * - 支持 MySQL、PostgreSQL、SQL Server、Oracle 等多种数据库的 CDC 捕获
 */
@Service
public class DebeziumEmbeddedManager {

    private final MappingMapper mappingMapper;
    private final DataSourceMapper dataSourceMapper;
    private final CdcRoutingService routingService;
    private final ChangeLogMapper changeLogMapper;

    private final Map<Long, EngineHolder> engines = new ConcurrentHashMap<>();

    public DebeziumEmbeddedManager(MappingMapper mappingMapper,
                                   DataSourceMapper dataSourceMapper,
                                   CdcRoutingService routingService,
                                   ChangeLogMapper changeLogMapper) {
        this.mappingMapper = mappingMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.routingService = routingService;
        this.changeLogMapper = changeLogMapper;
    }

    /**
     * 定时对齐“数据库里启用的映射配置”与“当前运行的 Debezium 引擎”。
     * <p>
     * 典型场景：
     * - 用户在页面把某条 mapping enabled=1：下一次 reconcile 会启动引擎开始抓取
     * - 用户把 enabled=0：下一次 reconcile 会停止引擎释放资源
     */
    @Scheduled(fixedDelayString = "${cdc.reconcile-interval-ms:10000}")
    public void reconcile() {
        List<MappingEntity> enabled = mappingMapper.selectEnabledForCdc();

        // 启动或更新
        for (MappingEntity m : enabled) {
            if (m.getId() == null || m.getSourceDataSourceId() == null) {
                continue;
            }
            EngineHolder holder = engines.get(m.getId());
            if (holder != null && holder.running) {
                continue;
            }
            // 防止重复启动：先停后启（幂等）
            stopEngine(m.getId());
            startEngine(m);
        }

        // 停止已不再启用的
        for (Long mappingId : engines.keySet()) {
            boolean stillEnabled = enabled.stream().anyMatch(m -> Objects.equals(m.getId(), mappingId));
            if (!stillEnabled) {
                stopEngine(mappingId);
            }
        }
    }

    private void startEngine(MappingEntity mapping) {
        DataSourceEntity ds = dataSourceMapper.selectById(mapping.getSourceDataSourceId());
        if (ds == null) {
            return;
        }
        String dbType = ds.getDbType() == null ? "" : ds.getDbType().toLowerCase();
        
        Properties props = buildConnectorProperties(mapping, ds, dbType);
        if (props == null) {
            // 不支持的数据库类型
            return;
        }

        String engineName = "mapping-" + mapping.getId();
        props.setProperty("name", engineName);

        // 通用配置：offset 和 schema history 存储
        File offsetDir = new File("./offsets");
        if (!offsetDir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            offsetDir.mkdirs();
        }
        String filename = new File(offsetDir, "mapping-" + mapping.getId() + ".dat").getAbsolutePath();
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        props.setProperty("offset.storage.file.filename", filename);
        props.setProperty("offset.flush.interval.ms", "1000");

        String schemaHistoryFile = new File(offsetDir, "schema-history-" + mapping.getId() + ".dat").getAbsolutePath();
        props.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        props.setProperty("schema.history.internal.file.filename", schemaHistoryFile);

        // Decimal 处理：统一使用 string 模式，避免 base64 编码
        props.setProperty("decimal.handling.mode", "string");
        props.setProperty("include.schema.changes", "false");

        EngineHolder holder = new EngineHolder(mapping.getId());

        DebeziumEngine<ChangeEvent<String, String>> engine =
                DebeziumEngine.create(Json.class)
                        .using(props)
                        .notifying(record -> {
                            String value = record.value();
                            if (value != null) {
                                holder.lastHeartbeat = Instant.now();
                                routingService.handleEvent(value);
                            }
                        })
                        .build();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "debezium-" + engineName);
            t.setDaemon(true);
            return t;
        });

        holder.engine = engine;
        holder.executor = executor;
        holder.running = true;

        executor.submit(engine);
        engines.put(mapping.getId(), holder);
    }

    /**
     * 根据数据库类型构建 Debezium 连接器配置
     */
    private Properties buildConnectorProperties(MappingEntity mapping, DataSourceEntity ds, String dbType) {
        Properties props = new Properties();
        String sourceSchema = mapping.getSourceSchema();
        String sourceTable = mapping.getSourceTable();
        String dbName = ds.getDbName();

        switch (dbType) {
            case "mysql":
                props.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
                props.setProperty("database.hostname", ds.getHost());
                props.setProperty("database.port", String.valueOf(ds.getPort()));
                props.setProperty("database.user", ds.getUsername());
                props.setProperty("database.password", ds.getPassword());
                props.setProperty("database.connectionTimeZone", "Asia/Shanghai");
                props.setProperty("database.server.id", String.valueOf(5400 + mapping.getId()));
                props.setProperty("topic.prefix", "mysql-dbserver" + mapping.getId());
                props.setProperty("database.include.list", dbName);
                props.setProperty("table.include.list", dbName + "." + sourceTable);
                break;

            case "postgresql":
            case "postgres":
                props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
                props.setProperty("database.hostname", ds.getHost());
                props.setProperty("database.port", String.valueOf(ds.getPort()));
                props.setProperty("database.user", ds.getUsername());
                props.setProperty("database.password", ds.getPassword());
                props.setProperty("database.dbname", dbName);
                props.setProperty("topic.prefix", "postgres-dbserver" + mapping.getId());
                // PostgreSQL 需要指定 publication 和 slot
                props.setProperty("publication.autocreate.mode", "filtered");
                props.setProperty("slot.name", "debezium_slot_" + mapping.getId());
                // schema 和 table 过滤
                String pgSchema = sourceSchema != null && !sourceSchema.isEmpty() ? sourceSchema : "public";
                props.setProperty("schema.include.list", pgSchema);
                props.setProperty("table.include.list", pgSchema + "." + sourceTable);
                break;

            case "sqlserver":
            case "mssql":
                props.setProperty("connector.class", "io.debezium.connector.sqlserver.SqlServerConnector");
                props.setProperty("database.hostname", ds.getHost());
                props.setProperty("database.port", String.valueOf(ds.getPort()));
                props.setProperty("database.user", ds.getUsername());
                props.setProperty("database.password", ds.getPassword());
                props.setProperty("database.dbname", dbName);
                // SQL Server 默认从驱动 12 开始 encrypt=true，如果使用自签名证书会导致
                // “PKIX path building failed” 错误。这里显式信任服务器证书，避免开发/内网环境连接失败。
                // 如果生产环境希望严格校验证书，可改为通过 JVM trustStore 配置 CA 证书，并将该配置移除或设为 false。
                props.setProperty("database.encrypt", "true");
                props.setProperty("database.trustServerCertificate", "true");
                props.setProperty("topic.prefix", "sqlserver-dbserver" + mapping.getId());
                // SQL Server 需要指定 database.names
                props.setProperty("database.names", dbName);
                // schema 和 table 过滤
                String mssqlSchema = sourceSchema != null && !sourceSchema.isEmpty() ? sourceSchema : "dbo";
                props.setProperty("schema.include.list", mssqlSchema);
                props.setProperty("table.include.list", mssqlSchema + "." + sourceTable);
                break;

            case "oracle":
                props.setProperty("connector.class", "io.debezium.connector.oracle.OracleConnector");
                props.setProperty("database.hostname", ds.getHost());
                props.setProperty("database.port", String.valueOf(ds.getPort()));
                props.setProperty("database.user", ds.getUsername());
                props.setProperty("database.password", ds.getPassword());
                props.setProperty("database.dbname", dbName);
                props.setProperty("topic.prefix", "oracle-dbserver" + mapping.getId());
                String oracleSchema = sourceSchema != null && !sourceSchema.isEmpty() ? sourceSchema.toUpperCase() : ds.getUsername().toUpperCase();
                props.setProperty("schema.include.list", oracleSchema);
                props.setProperty("table.include.list", oracleSchema + "." + sourceTable.toUpperCase());
                props.setProperty("log.mining.strategy", "online_catalog");
                break;

            case "mariadb":
                props.setProperty("connector.class", "io.debezium.connector.mariadb.MariaDbConnector");
                props.setProperty("database.hostname", ds.getHost());
                props.setProperty("database.port", String.valueOf(ds.getPort()));
                props.setProperty("database.user", ds.getUsername());
                props.setProperty("database.password", ds.getPassword());
                props.setProperty("database.connectionTimeZone", "Asia/Shanghai");
                props.setProperty("database.server.id", String.valueOf(5400 + mapping.getId()));
                props.setProperty("topic.prefix", "mariadb-dbserver" + mapping.getId());
                props.setProperty("database.include.list", dbName);
                props.setProperty("table.include.list", dbName + "." + sourceTable);
                break;

            case "db2":
                props.setProperty("connector.class", "io.debezium.connector.db2.Db2Connector");
                props.setProperty("database.hostname", ds.getHost());
                props.setProperty("database.port", String.valueOf(ds.getPort()));
                props.setProperty("database.user", ds.getUsername());
                props.setProperty("database.password", ds.getPassword());
                props.setProperty("database.dbname", dbName);
                props.setProperty("topic.prefix", "db2-dbserver" + mapping.getId());
                // Db2 table.include.list 格式为 schema.table
                String db2Schema = sourceSchema != null && !sourceSchema.isEmpty() ? sourceSchema.toUpperCase() : ds.getUsername().toUpperCase();
                props.setProperty("table.include.list", db2Schema + "." + sourceTable.toUpperCase());
                break;

            case "mongodb":
                props.setProperty("connector.class", "io.debezium.connector.mongodb.MongoDbConnector");
                String mongoUri = buildMongoConnectionString(ds);
                props.setProperty("mongodb.connection.string", mongoUri);
                props.setProperty("topic.prefix", "mongodb-dbserver" + mapping.getId());
                // 只捕获指定库下的指定集合，格式 databaseName.collectionName
                props.setProperty("collection.include.list", dbName + "." + sourceTable);
                break;

            default:
                return null;
        }

        return props;
    }

    /** 构建 MongoDB 连接字符串，用于 Debezium MongoDB 连接器 */
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

    private void stopEngine(Long mappingId) {
        EngineHolder holder = engines.remove(mappingId);
        if (holder != null) {
            try {
                holder.running = false;
                holder.engine.close();
            } catch (IOException ignored) {
            }
            holder.executor.shutdownNow();
        }
    }

    private static class EngineHolder {
        private final Long mappingId;
        private DebeziumEngine<ChangeEvent<String, String>> engine;
        private ExecutorService executor;
        private volatile boolean running;
        private volatile Instant lastHeartbeat;

        private EngineHolder(Long mappingId) {
            this.mappingId = mappingId;
        }
    }

    /**
     * 引擎运行状态快照 DTO。
     */
    public static class EngineStatus {
        public Long mappingId;
        public String mappingName;
        public String sourceTable;
        public String target;
        public String targetType;
        public boolean running;
        public Instant lastHeartbeat;
        public Long idleSeconds;
        public String lastErrorMessage;
    }

    /**
     * 提供给监控接口使用的运行状态快照。
     */
    public List<EngineStatus> snapshotStatus() {
        List<EngineStatus> result = new ArrayList<>();
        List<MappingEntity> enabled = mappingMapper.selectEnabledForCdc();
        Instant now = Instant.now();
        for (MappingEntity m : enabled) {
            EngineHolder holder = engines.get(m.getId());
            EngineStatus s = new EngineStatus();
            s.mappingId = m.getId();
            s.mappingName = m.getName();
            s.sourceTable = (m.getSourceSchema() != null && !m.getSourceSchema().isEmpty()
                    ? m.getSourceSchema() + "." : "") + m.getSourceTable();
            if ("DB".equalsIgnoreCase(m.getTargetType())) {
                s.target = (m.getTargetSchema() != null && !m.getTargetSchema().isEmpty()
                        ? m.getTargetSchema() + "." : "") + (m.getTargetTable() != null ? m.getTargetTable() : "");
            } else {
                s.target = m.getTargetType();
            }
            s.targetType = m.getTargetType();
            s.running = holder != null && holder.running;
            s.lastHeartbeat = holder != null ? holder.lastHeartbeat : null;
            if (s.lastHeartbeat != null) {
                s.idleSeconds = Math.max(0, Duration.between(s.lastHeartbeat, now).getSeconds());
            } else {
                s.idleSeconds = null;
            }
            ChangeLogEntity lastError = changeLogMapper.selectLastErrorByMapping(m.getId());
            s.lastErrorMessage = lastError != null ? lastError.getErrorMessage() : null;
            result.add(s);
        }
        return result;
    }
}

