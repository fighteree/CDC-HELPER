## CDC Helper（技术研究版）

**CDC Helper** 是一个基于 Debezium Embedded 的轻量级 CDC 管理平台，用于做数据库变更捕获与路由的**技术研究与演示**。

- 后端：`Spring Boot + MyBatis`，内嵌 Debezium Engine，负责捕获源库变更并路由到 DB / HTTP / MQ。
- 前端：`Vue3 + Vite + Element Plus`，提供数据源管理、映射配置、运行状态监控、变更日志等页面。

> **重要声明：**
>
> - 本项目仅供个人/团队**技术研究与学习**使用，**严禁任何形式的商业用途**。
> - 示例代码和配置不保证生产可用性，由此产生的一切风险由使用者自行承担。

作者：**一只大笨熊**

---

## 一、功能概览

- **数据源管理（/data-sources）**
  - 管理 CDC 源库 / 目标库连接信息。
  - 支持多种数据库类型：MySQL / MariaDB / PostgreSQL / SQL Server / Oracle / Db2 / MongoDB 等。
  - 一键“测试连接”：基于 JDBC 或 Mongo 驱动做连通性检查。
  - 支持导出数据源列表为 Excel。

- **映射配置管理（/mappings）**
  - 以“映射”为中心描述：**源数据源 + 源表 → 目标类型(DB/HTTP/MQ)**。
  - DB → DB 同步：
    - 支持源/目标表下拉选择（后端用 JDBC MetaData 自动拉取表/字段）。
    - 支持自动生成字段映射（按字段顺序一一对应）。
    - 支持标记主键字段，用于 UPDATE / DELETE 的 WHERE 条件。
    - 支持简单表达式：
      - `${source.col}` 或 `${source.table.col}` 取源字段值；
      - 支持**纯数值运算**：例如 `${source.age}*10`，后端自动求值为数值 130，再按目标列类型写入。
  - HTTP 目标：
    - 配置 URL / Method / Headers / 超时 / 重试次数。
    - 将变更数据（外加 `operation` 字段）作为 JSON 请求体发送。
  - MQ 目标：
    - Kafka / RabbitMQ / RocketMQ 动态发送（基于配置构建客户端）。

- **运行状态监控（/monitor）**
  - **CDC 引擎状态面板**：
    - 每条启用映射对应一个 Debezium 引擎的运行状态：
      - 映射 ID、名称、源表、目标、目标类型。
      - `running`：当前是否在运行。
      - `lastHeartbeat`：最后一次收到 CDC 事件的时间。
      - `idleSeconds`：距离最后一次事件的空闲秒数（用于判断是否“卡住/长时间无新数据”）。
      - `lastErrorMessage`：最近一条失败变更日志的错误信息（包括 SQL Server Agent 未启动、CDC 未启用等）。
    - 支持“仅显示异常/空闲过久”的过滤视图，方便快速定位有问题的映射。
  - **数据源健康检查**：
    - 一次性对所有启用数据源执行连通性检测（基于现有测试连接逻辑）。
    - 展示每个数据源当前是否可用、检测时间。

- **变更日志 & 一键重放（/logs）**
  - 记录每条 CDC 事件的处理结果：
    - 映射 ID、源表、操作类型（INSERT/UPDATE/DELETE）、目标类型、是否成功、耗时、错误信息、原始 Debezium 事件 JSON。
  - 日志筛选：
    - 按目标类型（DB / HTTP / MQ）、操作类型、结果（成功/失败）、时间范围、关键字（映射 ID / 源表 / 操作）过滤。
  - **失败日志一键重放**：
    - 对失败日志提供“重放”按钮，会基于当时的原始 CDC 事件 JSON 重新走一遍路由逻辑。
    - 支持查看重放次数、最后一次重放时间。
    - 前端弹窗与后端接口都包含**ABA 风险**提示：重放基于的是历史事件，当前源库/目标库状态可能已经不同。

---

## 二、项目结构

- `bakend/`  
  Spring Boot 后端：
  - `com.yzdbx.cdc.cdc`：CDC 核心模块（Debezium 管理、事件路由、DB Writer 等）。
    - `DebeziumEmbeddedManager`：管理各映射对应的 Debezium Engine，提供运行状态快照接口。
    - `CdcRoutingService`：接收 CDC 事件 JSON，执行字段映射与路由（DB / HTTP / MQ），记录变更日志。
  - `com.yzdbx.cdc.controller`：REST 控制器：
    - `MappingController`：映射 CRUD。
    - `ChangeLogController`：变更日志查询 + 单条重放。
    - `MonitorController`：运行状态 & 数据源健康检查接口。
  - `com.yzdbx.cdc.service`：
    - `DataSourceService`：数据源管理 + JDBC/Mongo 元数据 & 测连通。
    - `MappingService`：映射聚合 CRUD。
    - `ChangeLogService`：变更日志异步写入。
  - `src/main/resources/db/schema.sql`：管理库建表脚本（数据源、映射、HTTP/MQ 目标、变更日志）。

- `ui/cdc-ui/`  
  Vue3 前端：
  - `src/router/index.js`：前端路由：
    - `/data-sources`、`/mappings`、`/logs`、`/monitor`。
  - `src/views/data-source/DataSourceList.vue`：数据源管理页面。
  - `src/views/mapping/MappingList.vue`：映射配置页面（字段映射、表达式编辑）。
  - `src/views/log/ChangeLogList.vue`：变更日志 + 一键重放。
  - `src/views/monitor/EngineMonitor.vue`：CDC 引擎状态 + 数据源健康监控。
  - `src/App.vue`：整体布局（左侧菜单 + 顶部标题 + 作者徽标 + 免责声明）。

---

## 三、开发环境启动

### 1. 后端（Spring Boot）

工作目录：`bakend`：

```bash
cd bakend
mvn spring-boot:run
```

- 默认端口：`http://localhost:8080`
- API 前缀：`/api`，例如：
  - `/api/data-sources`
  - `/api/mappings`
  - `/api/change-logs`
  - `/api/monitor/engines`
  - `/api/monitor/datasources`

> **SQL Server CDC 前置条件（示例）：**
>
> - 启用数据库 CDC：`EXEC sys.sp_cdc_enable_db @dbname = N'TestDB';`
> - 启用表 CDC：`EXEC sys.sp_cdc_enable_table @source_schema = N'dbo', @source_name = N'USER', @role_name = NULL, @supports_net_changes = 1;`
> - 确保 SQL Server Agent 已启动。

### 2. 前端（Vue3 + Vite + Element Plus）

工作目录：`ui/cdc-ui`：

```bash
cd ui/cdc-ui
npm install
npm run dev
```

- 默认端口：`http://localhost:5173`
- Vite 代理会将 `/api` 转发到 `http://localhost:8080`，开发时直接访问前端地址即可。

常见入口：

- 数据源管理：`http://localhost:5173/#/data-sources`
- 映射配置：`http://localhost:5173/#/mappings`
- 变更日志：`http://localhost:5173/#/logs`
- 运行状态监控：`http://localhost:5173/#/monitor`

---

## 四、统一打包为可运行 JAR

> 前后端统一由 `bakend` 的 Maven 项目驱动打包，生成一个包含前端静态资源的 fat-jar。

### 打包命令

在项目根目录或 `bakend` 目录执行：

```bash
cd bakend
mvn clean package -DskipTests
```

Maven 过程（简要）：

1. 使用 `frontend-maven-plugin` 进入 `../ui/cdc-ui`：
   - 安装 Node / npm（如构建机无全局 node）。
   - 执行 `npm install`。
   - 执行 `npm run build`，生成 `ui/cdc-ui/dist`。
2. 使用 `maven-resources-plugin`：
   - 将 `ui/cdc-ui/dist` 复制到后端编译输出目录：`bakend/target/classes/static`。
3. 使用 `spring-boot-maven-plugin`：
   - 生成 fat-jar：`bakend/target/cdc-backend-0.0.1-SNAPSHOT.jar`。

### 运行 JAR

```bash
cd bakend/target
java -jar cdc-backend-0.0.1-SNAPSHOT.jar
```

启动后：

- 后端 API：`http://localhost:8080/api/...`
- 前端页面：由 Spring Boot 静态资源提供：
  - 访问 `http://localhost:8080/index.html`（具体以构建产物为准）。

---

## 五、Git 与忽略规则简述

- 根目录为唯一 Git 仓库根。
- `.gitignore` 已根据前后端代码约定：
  - 忽略 `ui/cdc-ui/node_modules`、`ui/cdc-ui/dist` 等前端依赖/构建产物。
  - 忽略 `bakend/target`、`bakend/offsets`、`bakend/logs` 等后端构建与运行时文件。
  - 忽略 `.env*`、IDE 配置、系统临时文件等。

---

## 六、推荐使用姿势（开发 & 调试）

1. 在“数据源管理”中配置并测试源库/目标库连接。
2. 在“映射配置”中创建或编辑映射规则：
   - 先选择源/目标表；
   - 使用“按顺序生成映射”一键生成字段对；
   - 根据需要编辑表达式（例如 `${source.amount}*100`）。
3. 在源库中触发 INSERT/UPDATE/DELETE：
   - 通过“运行状态”页面确认对应映射引擎处于运行中且有心跳；
   - 通过“变更日志”查看每条 CDC 事件的处理结果。
4. 对失败记录，如确有需要，可谨慎使用“一键重放”，注意 ABA 风险提示。

> 再次强调：本项目为**学习与实验用 Demo**，未经过严谨的生产级测试与审计，任何用于生产/商业场景的行为请自行评估并承担风险。 

