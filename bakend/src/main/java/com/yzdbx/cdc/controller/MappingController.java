package com.yzdbx.cdc.controller;

import com.yzdbx.cdc.model.dto.MappingDetailDTO;
import com.yzdbx.cdc.model.entity.MappingEntity;
import com.yzdbx.cdc.service.MappingService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mappings")
@CrossOrigin(origins = "*")
public class MappingController {

    private final MappingService mappingService;

    public MappingController(MappingService mappingService) {
        this.mappingService = mappingService;
    }

    /** 分页列表，返回 { list, total } */
    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "targetType", required = false) String targetType,
                                    @RequestParam(value = "enabled", required = false) Integer enabled,
                                    @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
                                    @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize) {
        return mappingService.list(keyword, targetType, enabled, pageNum, pageSize);
    }

    @GetMapping("/{id}")
    // 详情接口：返回 mapping + fields +（按 targetType 返回 httpTarget/mqTarget）
    public MappingDetailDTO detail(@PathVariable Long id) {
        return mappingService.detail(id);
    }

    @PostMapping
    // 创建：DTO 中包含主表、字段映射、HTTP/MQ 配置
    public Map<String, Object> create(@RequestBody MappingDetailDTO dto) {
        Long id = mappingService.create(dto.getMapping(), dto.getFields(), dto.getHttpTarget(), dto.getMqTarget());
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        return resp;
    }

    @PutMapping("/{id}")
    // 更新：id 以 path 为准，避免前端漏传/传错
    public void update(@PathVariable Long id, @RequestBody MappingDetailDTO dto) {
        MappingEntity mapping = dto.getMapping();
        mapping.setId(id);
        mappingService.update(mapping, dto.getFields(), dto.getHttpTarget(), dto.getMqTarget());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mappingService.delete(id);
    }
}

