package com.yzdbx.cdc.controller;

import com.yzdbx.cdc.mapper.ChangeLogMapper;
import com.yzdbx.cdc.model.entity.ChangeLogEntity;
import com.yzdbx.cdc.cdc.CdcRoutingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/change-logs")
@CrossOrigin(origins = "*")
public class ChangeLogController {

    private final ChangeLogMapper changeLogMapper;
    private final CdcRoutingService routingService;

    public ChangeLogController(ChangeLogMapper changeLogMapper,
                               CdcRoutingService routingService) {
        this.changeLogMapper = changeLogMapper;
        this.routingService = routingService;
    }

    /** 分页查询，支持时间范围；返回 { list, total } */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(value = "mappingId", required = false) Long mappingId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "success", required = false) Integer success,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "fromTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime fromTime,
            @RequestParam(value = "toTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime toTime,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize <= 0 || pageSize > 500) pageSize = 20;
        int offset = (pageNum - 1) * pageSize;

        int total = changeLogMapper.countByCondition(mappingId, targetType, success, operation, keyword, fromTime, toTime);
        List<ChangeLogEntity> list = changeLogMapper.selectByCondition(
                mappingId, targetType, success, operation, keyword, fromTime, toTime, offset, pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return result;
    }

    /**
     * 失败日志“一键重放”：
     * - 根据日志里的 raw_data_json 原样再走一遍 CDC 路由逻辑
     * - 仅建议在充分理解 ABA 风险后使用
     */
    @PostMapping("/{id}/replay")
    public Map<String, Object> replay(@PathVariable("id") Long id) {
        Map<String, Object> resp = new HashMap<>();
        ChangeLogEntity log = changeLogMapper.selectById(id);
        if (log == null) {
            resp.put("success", false);
            resp.put("message", "日志不存在");
            return resp;
        }
        // 提示 ABA 风险：重放旧事件可能与当前数据库状态不一致
        resp.put("abaWarning",
                "重放是基于历史 CDC 事件重新执行一次映射，" +
                        "如果源库在此期间发生过多次变更，可能出现 ABA 问题：即当前行状态已不同于当时，" +
                        "重放可能导致目标库与业务期望不一致，请在确认风险后使用。");

        try {
            // 原样重放：再次交给 CDC 路由服务处理原始 Debezium 事件
            routingService.handleEvent(log.getRawDataJson());
            changeLogMapper.increaseReplayCount(id);
            resp.put("success", true);
            resp.put("message", "已触发重放，请在变更日志中查看新的记录");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "重放失败：" + e.getMessage());
        }
        return resp;
    }
}

