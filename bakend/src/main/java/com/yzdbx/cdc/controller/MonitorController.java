package com.yzdbx.cdc.controller;

import com.yzdbx.cdc.cdc.DebeziumEmbeddedManager;
import com.yzdbx.cdc.mapper.DataSourceMapper;
import com.yzdbx.cdc.model.entity.DataSourceEntity;
import com.yzdbx.cdc.service.DataSourceService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行状态与健康检查接口：
 * - 引擎运行状态（按映射维度）
 * - 数据源连通性简单探测
 */
@RestController
@RequestMapping("/api/monitor")
@CrossOrigin(origins = "*")
public class MonitorController {

    private final DebeziumEmbeddedManager debeziumManager;
    private final DataSourceMapper dataSourceMapper;
    private final DataSourceService dataSourceService;

    public MonitorController(DebeziumEmbeddedManager debeziumManager,
                             DataSourceMapper dataSourceMapper,
                             DataSourceService dataSourceService) {
        this.debeziumManager = debeziumManager;
        this.dataSourceMapper = dataSourceMapper;
        this.dataSourceService = dataSourceService;
    }

    @GetMapping("/engines")
    public List<DebeziumEmbeddedManager.EngineStatus> engines() {
        return debeziumManager.snapshotStatus();
    }

    /**
     * 数据源健康检查：
     * - 仅做即时探测，返回当前是否可连与最近一次检测时间
     * - 后续如需更复杂的 backoff 策略，可在 Service 层增加缓存与定时任务
     */
    @GetMapping("/datasources")
    public Map<String, Object> dataSourceHealth() {
        Map<String, Object> result = new HashMap<>();
        List<DataSourceEntity> all = dataSourceMapper.selectAll(null, null, 1, 0, 1000);
        Map<Long, Map<String, Object>> items = new HashMap<>();
        Instant now = Instant.now();
        for (DataSourceEntity ds : all) {
            Map<String, Object> m = new HashMap<>();
            boolean ok = dataSourceService.testConnectionInternal(ds);
            m.put("id", ds.getId());
            m.put("name", ds.getName());
            m.put("dbType", ds.getDbType());
            m.put("ok", ok);
            m.put("checkedAt", now.toString());
            items.put(ds.getId(), m);
        }
        result.put("items", items.values());
        return result;
    }
}

