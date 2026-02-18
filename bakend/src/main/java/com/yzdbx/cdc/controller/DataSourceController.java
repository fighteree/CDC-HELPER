package com.yzdbx.cdc.controller;

import com.yzdbx.cdc.model.entity.DataSourceEntity;
import com.yzdbx.cdc.service.DataSourceService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data-sources")
@CrossOrigin(origins = "*")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    /** 分页列表，返回 { list, total } */
    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "dbType", required = false) String dbType,
                                    @RequestParam(value = "status", required = false) Integer status,
                                    @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
                                    @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        return dataSourceService.list(keyword, dbType, status, pageNum, pageSize);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody DataSourceEntity entity) {
        Long id = dataSourceService.create(entity);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        return resp;
    }

    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody DataSourceEntity entity) {
        entity.setId(id);
        dataSourceService.update(entity);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        dataSourceService.delete(id);
    }

    /**
     * 根据已保存的数据源配置测试连接（列表页“测试连接”按钮使用）
     */
    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id) {
        boolean ok = dataSourceService.testConnection(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", ok);
        return resp;
    }

    /**
     * 根据前端传入的配置直接测试连接，而不落库。
     * 适用于“新建/编辑弹窗中，先不保存就想测试当前填写是否可连通”的场景。
     */
    @PostMapping("/test-connection")
    public Map<String, Object> testByBody(@RequestBody DataSourceEntity entity) {
        boolean ok = dataSourceService.testConnectionInternal(entity);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", ok);
        return resp;
    }

    @GetMapping("/{id}/tables")
    public List<DataSourceService.TableMeta> tables(@PathVariable Long id,
                                                    @RequestParam(value = "schema", required = false) String schema) {
        return dataSourceService.listTables(id, schema);
    }

    @GetMapping("/{id}/columns")
    public List<DataSourceService.ColumnMeta> columns(@PathVariable Long id,
                                                      @RequestParam(value = "schema", required = false) String schema,
                                                      @RequestParam("table") String table) {
        return dataSourceService.listColumns(id, schema, table);
    }
}

