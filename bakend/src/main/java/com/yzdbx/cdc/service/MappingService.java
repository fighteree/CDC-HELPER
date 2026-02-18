package com.yzdbx.cdc.service;

import com.yzdbx.cdc.mapper.HttpTargetMapper;
import com.yzdbx.cdc.mapper.MqTargetMapper;
import com.yzdbx.cdc.mapper.MappingFieldMapper;
import com.yzdbx.cdc.mapper.MappingMapper;
import com.yzdbx.cdc.model.dto.MappingDetailDTO;
import com.yzdbx.cdc.model.entity.HttpTargetEntity;
import com.yzdbx.cdc.model.entity.MqTargetEntity;
import com.yzdbx.cdc.model.entity.MappingEntity;
import com.yzdbx.cdc.model.entity.MappingFieldEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 映射配置服务：
 * <p>
 * 一条映射（MappingEntity）描述：
 * - CDC 源端（sourceDataSourceId + sourceTable）
 * - 同步目标类型（DB/HTTP/MQ）及对应目标配置
 * - 字段映射（MappingFieldEntity 列对列、主键标记、表达式）
 * <p>
 * 这里负责：
 * - 列表查询（带条件）
 * - 详情聚合（mapping + fields + httpTarget/mqTarget）
 * - create/update/delete 的事务性落库
 */
@Service
public class MappingService {

    private final MappingMapper mappingMapper;
    private final MappingFieldMapper mappingFieldMapper;
    private final HttpTargetMapper httpTargetMapper;
    private final MqTargetMapper mqTargetMapper;

    public MappingService(MappingMapper mappingMapper,
                          MappingFieldMapper mappingFieldMapper,
                          HttpTargetMapper httpTargetMapper,
                          MqTargetMapper mqTargetMapper) {
        this.mappingMapper = mappingMapper;
        this.mappingFieldMapper = mappingFieldMapper;
        this.httpTargetMapper = httpTargetMapper;
        this.mqTargetMapper = mqTargetMapper;
    }

    /** 分页列表，返回 { list, total } */
    public Map<String, Object> list(String keyword, String targetType, Integer enabled, int pageNum, int pageSize) {
        if (!StringUtils.hasText(keyword)) keyword = null;
        if (!StringUtils.hasText(targetType)) targetType = null;
        if (pageNum < 1) pageNum = 1;
        if (pageSize <= 0 || pageSize > 500) pageSize = 20;
        int offset = (pageNum - 1) * pageSize;

        int total = mappingMapper.countByCondition(keyword, targetType, enabled);
        List<MappingEntity> list = mappingMapper.selectAll(keyword, targetType, enabled, offset, pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return result;
    }

    public MappingDetailDTO detail(Long id) {
        // 详情需要聚合多表：cdc_mapping + cdc_mapping_field + (cdc_http_target/cdc_mq_target)
        MappingEntity mapping = mappingMapper.selectById(id);
        if (mapping == null) {
            return null;
        }
        List<MappingFieldEntity> fields = mappingFieldMapper.selectByMappingId(id);
        MappingDetailDTO dto = new MappingDetailDTO();
        dto.setMapping(mapping);
        dto.setFields(fields);
        if ("HTTP".equalsIgnoreCase(mapping.getTargetType())) {
            HttpTargetEntity httpTarget = httpTargetMapper.selectByMappingId(id);
            dto.setHttpTarget(httpTarget);
        } else if ("MQ".equalsIgnoreCase(mapping.getTargetType())) {
            MqTargetEntity mqTarget = mqTargetMapper.selectByMappingId(id);
            dto.setMqTarget(mqTarget);
        }
        return dto;
    }

    @Transactional
    public Long create(MappingEntity mapping,
                       List<MappingFieldEntity> fields,
                       HttpTargetEntity httpTarget,
                       MqTargetEntity mqTarget) {
        // 默认启用：创建后 CDC 引擎会在下次 reconcile 自动生效
        if (mapping.getEnabled() == null) {
            mapping.setEnabled(1);
        }
        mappingMapper.insert(mapping);
        Long mappingId = mapping.getId();
        if (!CollectionUtils.isEmpty(fields)) {
            for (MappingFieldEntity f : fields) {
                f.setMappingId(mappingId);
                // 未显式勾选则默认为非主键（update/delete 依赖主键拼 where）
                if (f.getIsPk() == null) {
                    f.setIsPk(0);
                }
            }
            mappingFieldMapper.bulkInsert(fields);
        }
        if ("HTTP".equalsIgnoreCase(mapping.getTargetType()) && httpTarget != null) {
            httpTarget.setMappingId(mappingId);
            // 兜底默认值：避免空值导致请求不可控
            if (httpTarget.getTimeoutMs() == null) {
                httpTarget.setTimeoutMs(5000);
            }
            if (httpTarget.getRetryTimes() == null) {
                httpTarget.setRetryTimes(3);
            }
            httpTargetMapper.insert(httpTarget);
        } else if ("MQ".equalsIgnoreCase(mapping.getTargetType()) && mqTarget != null) {
            mqTarget.setMappingId(mappingId);
            mqTargetMapper.insert(mqTarget);
        }
        return mappingId;
    }

    @Transactional
    public void update(MappingEntity mapping,
                       List<MappingFieldEntity> fields,
                       HttpTargetEntity httpTarget,
                       MqTargetEntity mqTarget) {
        mappingMapper.update(mapping);
        // 简化实现：字段映射采用“全量删除 + 全量插入”
        // 优点：实现简单；缺点：字段多时会有额外写入开销（后续可改为 diff 更新）
        mappingFieldMapper.deleteByMappingId(mapping.getId());
        if (!CollectionUtils.isEmpty(fields)) {
            for (MappingFieldEntity f : fields) {
                f.setMappingId(mapping.getId());
                if (f.getIsPk() == null) {
                    f.setIsPk(0);
                }
            }
            mappingFieldMapper.bulkInsert(fields);
        }
        // HTTP / MQ 目标配置
        // 同样采用“先删后插”，保证 targetType 切换时不会残留旧配置
        httpTargetMapper.deleteByMappingId(mapping.getId());
        mqTargetMapper.deleteByMappingId(mapping.getId());
        if ("HTTP".equalsIgnoreCase(mapping.getTargetType()) && httpTarget != null) {
            httpTarget.setMappingId(mapping.getId());
            if (httpTarget.getTimeoutMs() == null) {
                httpTarget.setTimeoutMs(5000);
            }
            if (httpTarget.getRetryTimes() == null) {
                httpTarget.setRetryTimes(3);
            }
            httpTargetMapper.insert(httpTarget);
        } else if ("MQ".equalsIgnoreCase(mapping.getTargetType()) && mqTarget != null) {
            mqTarget.setMappingId(mapping.getId());
            mqTargetMapper.insert(mqTarget);
        }
    }

    @Transactional
    public void delete(Long id) {
        mappingFieldMapper.deleteByMappingId(id);
        mappingMapper.deleteById(id);
    }
}

