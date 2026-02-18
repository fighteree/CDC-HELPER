package com.yzdbx.cdc.cdc;

import com.yzdbx.cdc.mapper.HttpTargetMapper;
import com.yzdbx.cdc.mapper.MqTargetMapper;
import com.yzdbx.cdc.mapper.MappingFieldMapper;
import com.yzdbx.cdc.mapper.MappingMapper;
import com.yzdbx.cdc.cdc.db.DbWriter;
import com.yzdbx.cdc.cdc.db.DbWriterFactory;
import com.yzdbx.cdc.model.entity.ChangeLogEntity;
import com.yzdbx.cdc.model.entity.HttpTargetEntity;
import com.yzdbx.cdc.model.entity.MqTargetEntity;
import com.yzdbx.cdc.model.entity.MappingEntity;
import com.yzdbx.cdc.model.entity.MappingFieldEntity;
import com.yzdbx.cdc.model.entity.DataSourceEntity;
import com.yzdbx.cdc.service.DataSourceService;
import com.yzdbx.cdc.service.ChangeLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CDC 事件路由服务（核心逻辑）：
 * <p>
 * 职责：
 * - 接收 Debezium Embedded Engine 推送的单条变更事件 JSON
 * - 解析出源库/源表/操作类型（INSERT/UPDATE/DELETE）
 * - 查询启用中的映射配置（MappingEntity + 字段映射）
 * - 根据 targetType 分发到：DB / HTTP / MQ
 * - 统一记录变更日志（成功/失败、耗时、原始事件）
 * <p>
 * 说明：
 * - 当前表达式求值仅做“占位符替换”，主要用于把源字段拼成目标字段值（例如拼接、常量等）
 * - DB 写入层已抽象为 DbWriter，方便逐步适配不同目标库方言/类型
 */
@Service
public class CdcRoutingService {

    private final ObjectMapper objectMapper;
    private final MappingMapper mappingMapper;
    private final MappingFieldMapper mappingFieldMapper;
    private final MqTargetMapper mqTargetMapper;
    private final HttpTargetMapper httpTargetMapper;
    private final ChangeLogService changeLogService;
    private final DataSourceService dataSourceService;
    private final DbWriterFactory dbWriterFactory;

    public CdcRoutingService(ObjectMapper objectMapper,
                             MappingMapper mappingMapper,
                             MappingFieldMapper mappingFieldMapper,
                             MqTargetMapper mqTargetMapper,
                             HttpTargetMapper httpTargetMapper,
                             ChangeLogService changeLogService,
                             DataSourceService dataSourceService,
                             DbWriterFactory dbWriterFactory) {
        this.objectMapper = objectMapper;
        this.mappingMapper = mappingMapper;
        this.mappingFieldMapper = mappingFieldMapper;
        this.mqTargetMapper = mqTargetMapper;
        this.httpTargetMapper = httpTargetMapper;
        this.changeLogService = changeLogService;
        this.dataSourceService = dataSourceService;
        this.dbWriterFactory = dbWriterFactory;
    }

    /**
     * 处理来自 Debezium 嵌入式引擎的变更事件。
     * <p>
     * Debezium JSON 事件结构大致为：{"payload": {"op": "...", "source": {...}, "before": {...}, "after": {...}}}
     * - op: c/u/d（create/update/delete）
     * - before/after: 行数据（delete 取 before，其它取 after）
     */
    public void handleEvent(String value) {
        Instant start = Instant.now();
        ChangeLogEntity log = new ChangeLogEntity();
        log.setSuccess(0);
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode()) {
                return;
            }
            // Debezium 操作类型：c=insert, u=update, d=delete
            String op = payload.path("op").asText(); // c/u/d
            JsonNode source = payload.path("source");
            String table = source.path("table").asText();
            // 对不同数据库的字段做兼容：
            // - MySQL / MariaDB / 部分场景：只有 db（库名），没有 schema
            // - PostgreSQL：db = 数据库名，schema = schema 名
            // - SQL Server：db = 数据库名，schema = schema 名（例如 dbo）
            // 为了能和 Mapping 配置里的 sourceSchema 正确匹配，这里优先使用 source.schema，其次回退到 source.db。
            String schema = null;
            if (source.hasNonNull("schema")) {
                schema = source.path("schema").asText();
            } else if (source.hasNonNull("db")) {
                schema = source.path("db").asText();
            }

            log.setSourceTable(table);

            String opName;
            switch (op) {
                case "c":
                    opName = "INSERT";
                    break;
                case "u":
                    opName = "UPDATE";
                    break;
                case "d":
                    opName = "DELETE";
                    break;
                default:
                    return;
            }
            log.setOperation(opName);

            // 1. 优先通过 Debezium 的逻辑名（serverName/name）解析出 mappingId，做到“一条引擎一个映射”的精确路由
            String serverName = null;
            if (source.hasNonNull("name")) {
                serverName = source.path("name").asText();
            } else if (source.hasNonNull("serverName")) {
                serverName = source.path("serverName").asText();
            }

            MappingEntity mapping = null;
            Long mappingIdFromServer = extractMappingIdFromServerName(serverName);
            if (mappingIdFromServer != null) {
                mapping = mappingMapper.selectById(mappingIdFromServer);
            }

            // 2. 兼容老逻辑：如果没解析出 mappingId，再按 schema + table 兜底查一把
            if (mapping == null) {
                mapping = mappingMapper.selectBySource(schema, table);
            }
            if (mapping == null || mapping.getEnabled() == null || mapping.getEnabled() != 1) {
                return;
            }
            log.setMappingId(mapping.getId());
            log.setTargetType(mapping.getTargetType());

            List<MappingFieldEntity> fields = mappingFieldMapper.selectByMappingId(mapping.getId());
            Map<String, Object> rowData = extractRowData(payload, op);

            // 根据字段映射构建目标数据
            Map<String, Object> targetRow = buildTargetRow(fields, rowData, mapping);

            // 根据目标类型路由
            switch (mapping.getTargetType()) {
                case "DB":
                    applyToTargetDb(mapping, fields, opName, targetRow);
                    break;
                case "HTTP":
                    // HTTP 一般希望发送“字段映射后的目标结构”，而不是 Debezium 原始行数据
                    sendToHttp(mapping, rowData, opName);
                    break;
                case "MQ":
                    sendToMq(mapping, targetRow);
                    break;
                default:
                    throw new IllegalArgumentException("未知 targetType: " + mapping.getTargetType());
            }

            log.setSuccess(1);
        } catch (Exception e) {
            log.setErrorMessage(e.getMessage());
        } finally {
            // 日志记录尽量做到“完整且不影响主流程”：
            // - rawDataJson 保存完整 Debezium 事件，便于后续排查与重放
            // - 异步写库，避免阻塞 CDC 线程
            log.setRawDataJson(value);
            if (log.getMappingId() != null) {
                log.setCostMs((int) Duration.between(start, Instant.now()).toMillis());
                changeLogService.saveAsync(log);
            }
        }
    }

    private Map<String, Object> extractRowData(JsonNode payload, String op) {
        JsonNode dataNode;
        if ("d".equals(op)) {
            dataNode = payload.path("before");
        } else {
            dataNode = payload.path("after");
        }
        Map<String, Object> map = new HashMap<>();
        if (dataNode == null || dataNode.isMissingNode() || dataNode.isNull()) {
            return map;
        }
        // Jackson JsonNode -> Java 基础类型，便于后续表达式/写入/发送
        dataNode.fields().forEachRemaining(entry -> {
            JsonNode v = entry.getValue();
            if (v.isNull()) {
                map.put(entry.getKey(), null);
            } else if (v.isNumber()) {
                map.put(entry.getKey(), v.numberValue());
            } else if (v.isBoolean()) {
                map.put(entry.getKey(), v.booleanValue());
            } else {
                map.put(entry.getKey(), v.asText());
            }
        });
        return map;
    }

    private Map<String, Object> buildTargetRow(List<MappingFieldEntity> fields,
                                               Map<String, Object> sourceRow,
                                               MappingEntity mapping) {
        Map<String, Object> target = new HashMap<>();
        if (CollectionUtils.isEmpty(fields)) {
            return target;
        }
        for (MappingFieldEntity f : fields) {
            Object val;
            if (StringUtils.hasText(f.getExpr())) {
                val = evalExpr(f.getExpr(), sourceRow, mapping);
            } else {
                val = sourceRow.get(f.getSourceCol());
            }
            // 自动类型判断策略：
            // - 表达式里出现算术运算时，evalExpr 会返回 Number，方便数值列写入
            // - 普通字段直接沿用 Debezium 解析后的类型（Number/Boolean/String）
            // 其余类型细节交给 JdbcParamBinder 根据目标列类型处理
            target.put(f.getTargetCol(), val);
        }
        return target;
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 非常简单的占位符替换表达式。
     * <p>
     * 目前仅支持把表达式中的 ${source.xxx} 替换为源行数据里的字段值：
     * - ${source.amount} -> sourceRow.get("amount")
     * - ${source.order_main.amount}（支持写表名/库名的“装饰”，最终仍取最后一段作为列名）
     * <p>
     * 注意：这里不是一个完整表达式引擎，只是第一版可用的“字符串模板替换”。
     */
    private Object evalExpr(String expr, Map<String, Object> sourceRow, MappingEntity mapping) {
        // 1. 先做占位符替换：${source.xxx} -> 源行数据里的字段值字符串
        if (!expr.contains("${")) {
            // 没有占位符，后面直接按“常量表达式”处理
        }
        String replaced = expr;
        Matcher m = PLACEHOLDER_PATTERN.matcher(expr);
        while (m.find()) {
            String path = m.group(1); // 例如 source.order_main.amount 或 source.amount
            if (!path.startsWith("source")) {
                continue;
            }
            String[] parts = path.split("\\.");
            String col = parts[parts.length - 1];
            Object val = sourceRow.get(col);
            replaced = replaced.replace("${" + path + "}", val == null ? "" : val.toString());
        }

        // 2. 如果表达式明显是一个“数值运算”（包含 + - * / 等），尝试在后端求值，返回 Number，
        //    这样后续 JdbcParamBinder 可以按目标列类型自动做 int/decimal 转换，避免 "13*10" 当成字符串。
        Object mathVal = tryEvalNumericExpression(replaced);
        if (mathVal != null) {
            return mathVal;
        }

        // 3. 否则，按字符串模板使用：例如 "prefix-${source.xxx}"
        return replaced;
    }


    private Object tryEvalNumericExpression(String expr) {
        if (expr == null) {
            return null;
        }
        String trimmed = expr.trim();
        // 粗略判断：包含运算符，且只由允许的字符组成
        if (!(trimmed.contains("+") || trimmed.contains("-") || trimmed.contains("*") || trimmed.contains("/"))) {
            return null;
        }
        if (!trimmed.matches("[0-9+\\-*/().\\s]+")) {
            // 出现了其它字符（字母等），认为不是纯数值运算，不做 eval
            return null;
        }
        try {
            // 使用简单的“逆波兰表达式”算法在 JVM 内部自行求值，避免依赖 ScriptEngine/Nashorn。
            return evalSimpleExpression(trimmed);
        } catch (Exception ignore) {
            // 计算失败则忽略，按字符串返回
        }
        return null;
    }

    /**
     * 仅支持由数字、小数点、括号和 +-* 组成的简单算术表达式。
     * 实现思路：
     * - 先用 shunting-yard 算法把中缀表达式转成后缀表达式
     * - 然后用栈计算 RPN，返回
     */
    private Double evalSimpleExpression(String expr) {
        java.util.Deque<Character> opStack = new java.util.ArrayDeque<>();
        java.util.List<String> output = new java.util.ArrayList<>();

        int n = expr.length();
        StringBuilder numberBuffer = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char ch = expr.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if ((ch >= '0' && ch <= '9') || ch == '.') {
                numberBuffer.append(ch);
                continue;
            }
            if (!numberBuffer.isEmpty()) {
                output.add(numberBuffer.toString());
                numberBuffer.setLength(0);
            }
            if (ch == '(') {
                opStack.push(ch);
            } else if (ch == ')') {
                while (!opStack.isEmpty() && opStack.peek() != '(') {
                    output.add(String.valueOf(opStack.pop()));
                }
                if (!opStack.isEmpty() && opStack.peek() == '(') {
                    opStack.pop();
                }
            } else if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                while (!opStack.isEmpty() && precedence(opStack.peek()) >= precedence(ch)) {
                    output.add(String.valueOf(opStack.pop()));
                }
                opStack.push(ch);
            } else {
                throw new IllegalArgumentException("不支持的字符: " + ch);
            }
        }
        if (numberBuffer.length() > 0) {
            output.add(numberBuffer.toString());
        }
        while (!opStack.isEmpty()) {
            char op = opStack.pop();
            if (op == '(' || op == ')') {
                continue;
            }
            output.add(String.valueOf(op));
        }

        // 计算 RPN
        java.util.Deque<Double> stack = new java.util.ArrayDeque<>();
        for (String token : output) {
            if (token.length() == 1 && "+-*/".indexOf(token.charAt(0)) >= 0) {
                double b = stack.pop();
                double a = stack.pop();
                switch (token.charAt(0)) {
                    case '+':
                        stack.push(a + b);
                        break;
                    case '-':
                        stack.push(a - b);
                        break;
                    case '*':
                        stack.push(a * b);
                        break;
                    case '/':
                        stack.push(a / b);
                        break;
                    default:
                        throw new IllegalArgumentException("不支持的运算符: " + token);
                }
            } else {
                stack.push(Double.parseDouble(token));
            }
        }
        if (stack.isEmpty()) {
            return null;
        }
        return stack.pop();
    }

    private int precedence(char op) {
        if (op == '+' || op == '-') return 1;
        if (op == '*' || op == '/') return 2;
        return 0;
    }

    /**
     * 简单的 DB→DB 同步实现（基于 DriverManager，适合作为第一版）
     */
    private void applyToTargetDb(MappingEntity mapping,
                                 List<MappingFieldEntity> fields,
                                 String op,
                                 Map<String, Object> targetRow) throws Exception {
        if (mapping.getTargetDataSourceId() == null) {
            throw new IllegalStateException("DB 同步需要配置 targetDataSourceId");
        }
        DataSourceEntity targetDs = dataSourceService.getById(mapping.getTargetDataSourceId());
        if (targetDs == null) {
            throw new IllegalStateException("目标数据源不存在: " + mapping.getTargetDataSourceId());
        }
        String jdbcUrl = StringUtils.hasText(targetDs.getJdbcUrl())
                ? targetDs.getJdbcUrl()
                : buildJdbcUrlFor(targetDs);
        // 预先获取目标表字段元数据，用于做合适的类型转换/IDENTITY处理等
        List<DataSourceService.ColumnMeta> columnMetaList =
                dataSourceService.listColumns(targetDs.getId(), mapping.getTargetSchema(), mapping.getTargetTable());
        Map<String, DataSourceService.ColumnMeta> columnMetaMap = new HashMap<>();
        for (DataSourceService.ColumnMeta cm : columnMetaList) {
            columnMetaMap.put(cm.getName(), cm);
        }
        DbWriter writer = dbWriterFactory.getWriter(targetDs);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, targetDs.getUsername(), targetDs.getPassword())) {
            conn.setAutoCommit(false);
            switch (op) {
                case "INSERT":
                    writer.insert(conn, targetDs, mapping, fields, targetRow, columnMetaMap);
                    break;
                case "UPDATE":
                    writer.update(conn, targetDs, mapping, fields, targetRow, columnMetaMap);
                    break;
                case "DELETE":
                    writer.delete(conn, targetDs, mapping, fields, targetRow, columnMetaMap);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的操作类型: " + op);
            }
            conn.commit();
        }
    }

    private String buildJdbcUrlFor(DataSourceEntity ds) {
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
                        + ";databaseName=" + ds.getDbName();
            case "db2":
                return "jdbc:db2://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + ds.getDbType());
        }
    }

    private void sendToMq(MappingEntity mapping, Map<String, Object> payload) throws Exception {
        MqTargetEntity mq = mqTargetMapper.selectByMappingId(mapping.getId());
        if (mq == null) {
            throw new IllegalStateException("MQ 目标未配置, mappingId=" + mapping.getId());
        }
        // MQ 发送内容统一为 JSON 字符串，便于消费者解析
        String body = objectMapper.writeValueAsString(payload);
        String type = mq.getMqType() == null ? "" : mq.getMqType().toUpperCase();
        switch (type) {
            case "KAFKA":
                sendKafkaDynamic(mq.getServerAddr(), mq.getTopic(), body);
                break;
            case "RABBITMQ":
                sendRabbitDynamic(mq.getServerAddr(), mq.getTopic(), mq.getRoutingKey(), body);
                break;
            case "ROCKETMQ":
                sendRocketDynamic(mq.getServerAddr(), mq.getTopic(), body);
                break;
            default:
                throw new IllegalArgumentException("不支持的 MQ 类型: " + mq.getMqType());
        }
    }

    private void sendKafkaDynamic(String servers, String topic, String body) {
        // 这里每次发送都创建 Producer：实现简单但性能一般，后续可做连接池/复用
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, body));
        }
    }

    private void sendRabbitDynamic(String uriOrHost, String exchange, String routingKey, String body) throws Exception {
        // 支持两种写法：
        // - amqp://user:pass@host:port/vhost（URI）
        // - host:port（简化写法）
        ConnectionFactory factory = new ConnectionFactory();
        if (uriOrHost != null && uriOrHost.startsWith("amqp")) {
            factory.setUri(uriOrHost);
        } else {
            // 简单场景下，uriOrHost 可以是 host:port
            String[] parts = uriOrHost.split(":");
            factory.setHost(parts[0]);
            if (parts.length > 1) {
                factory.setPort(Integer.parseInt(parts[1]));
            }
        }
        try (com.rabbitmq.client.Connection conn = factory.newConnection();
             Channel channel = conn.createChannel()) {
            channel.basicPublish(exchange, routingKey == null ? "" : routingKey, null, body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void sendRocketDynamic(String nameSrv, String topic, String body) throws Exception {
        // RocketMQ Producer 也可复用；当前实现为“每次发送创建/关闭”，优先保证易用
        DefaultMQProducer producer = new DefaultMQProducer("cdc-dynamic-producer");
        producer.setNamesrvAddr(nameSrv);
        try {
            producer.start();
            Message msg = new Message(topic, body.getBytes(StandardCharsets.UTF_8));
            producer.send(msg);
        } finally {
            producer.shutdown();
        }
    }

    /**
     * 从 Debezium 的 serverName/name 中解析出 mappingId。
     * 约定：
     * - 我们在构建连接器配置时使用 topic.prefix = "xxx" + mappingId，例如：sqlserver-dbserver6
     * - 这里通用地从末尾连续数字解析出 mappingId，避免强依赖前缀具体写法。
     */
    private Long extractMappingIdFromServerName(String serverName) {
        if (serverName == null || serverName.isEmpty()) {
            return null;
        }
        int end = serverName.length() - 1;
        int start = end;
        while (start >= 0 && Character.isDigit(serverName.charAt(start))) {
            start--;
        }
        if (start == end) {
            // 末尾没有数字
            return null;
        }
        String digits = serverName.substring(start + 1);
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 发送变更数据到 HTTP 接口
     */
    private void sendToHttp(MappingEntity mapping, Map<String, Object> payload, String operation) throws Exception {
        HttpTargetEntity httpTarget = httpTargetMapper.selectByMappingId(mapping.getId());
        if (httpTarget == null) {
            throw new IllegalStateException("HTTP 目标未配置, mappingId=" + mapping.getId());
        }

        String url = httpTarget.getUrl();
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("HTTP URL 未配置");
        }

        String method = StringUtils.hasText(httpTarget.getMethod())
                ? httpTarget.getMethod().toUpperCase()
                : "POST";

        // 构建请求体：基于映射后的 payload，再补充 operation 等元信息
        // 不直接修改入参，避免影响上层逻辑复用该 Map
        Map<String, Object> requestPayload = new HashMap<>(payload);
        requestPayload.put("operation", operation);
        String requestBody = objectMapper.writeValueAsString(requestPayload);

        // 解析请求头
        Map<String, String> headers = new HashMap<>();
        if (StringUtils.hasText(httpTarget.getHeadersJson())) {
            try {
                JsonNode headersNode = objectMapper.readTree(httpTarget.getHeadersJson());
                if (headersNode.isObject()) {
                    headersNode.fields().forEachRemaining(entry -> {
                        headers.put(entry.getKey(), entry.getValue().asText());
                    });
                }
            } catch (Exception e) {
                // 如果解析失败，忽略 headers
            }
        }

        // 默认 Content-Type
        if (!headers.containsKey("Content-Type") && !headers.containsKey("content-type")) {
            headers.put("Content-Type", "application/json; charset=UTF-8");
        }

        // 超时时间（默认 30 秒）
        int timeoutMs = httpTarget.getTimeoutMs() != null && httpTarget.getTimeoutMs() > 0
                ? httpTarget.getTimeoutMs()
                : 30000;

        // 重试次数（默认 0，不重试）
        int retryTimes = httpTarget.getRetryTimes() != null && httpTarget.getRetryTimes() >= 0
                ? httpTarget.getRetryTimes()
                : 0;

        // 执行 HTTP 请求（带重试）
        Exception lastException = null;
        for (int attempt = 0; attempt <= retryTimes; attempt++) {
            try {
                sendHttpRequest(url, method, headers, requestBody, timeoutMs);
                return; // 成功则返回
            } catch (Exception e) {
                lastException = e;
                if (attempt < retryTimes) {
                    // 等待后重试（指数退避：100ms, 200ms, 400ms...）
                    Thread.sleep(100L * (1L << attempt));
                }
            }
        }

        // 所有重试都失败，抛出最后一次的异常
        throw lastException != null ? lastException
                : new RuntimeException("HTTP 请求失败，已重试 " + retryTimes + " 次");
    }

    /**
     * 执行单次 HTTP 请求
     */
    private void sendHttpRequest(String url, String method, Map<String, String> headers,
                                 String requestBody, int timeoutMs) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs));

        // 设置请求头
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }

        // 设置请求方法和请求体
        switch (method) {
            case "GET":
                requestBuilder.GET();
                break;
            case "POST":
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                break;
            case "PUT":
                requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                break;
            case "DELETE":
                if (StringUtils.hasText(requestBody)) {
                    requestBuilder.method("DELETE", HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                } else {
                    requestBuilder.DELETE();
                }
                break;
            case "PATCH":
                requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                break;
            default:
                throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // 检查响应状态码（2xx 视为成功）
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new RuntimeException("HTTP 请求失败，状态码: " + statusCode
                    + ", 响应: " + response.body());
        }
    }

    // 类型转换逻辑已迁移到 cdc.db.JdbcParamBinder，writer 内部统一复用
}

