package com.yzdbx.cdc.model.dto;

import com.yzdbx.cdc.model.entity.MappingEntity;
import com.yzdbx.cdc.model.entity.MappingFieldEntity;
import com.yzdbx.cdc.model.entity.HttpTargetEntity;
import com.yzdbx.cdc.model.entity.MqTargetEntity;

import java.util.List;

public class MappingDetailDTO {

    private MappingEntity mapping;
    private List<MappingFieldEntity> fields;
    private HttpTargetEntity httpTarget;
    private MqTargetEntity mqTarget;

    public MappingEntity getMapping() {
        return mapping;
    }

    public void setMapping(MappingEntity mapping) {
        this.mapping = mapping;
    }

    public List<MappingFieldEntity> getFields() {
        return fields;
    }

    public void setFields(List<MappingFieldEntity> fields) {
        this.fields = fields;
    }

    public HttpTargetEntity getHttpTarget() {
        return httpTarget;
    }

    public void setHttpTarget(HttpTargetEntity httpTarget) {
        this.httpTarget = httpTarget;
    }

    public MqTargetEntity getMqTarget() {
        return mqTarget;
    }

    public void setMqTarget(MqTargetEntity mqTarget) {
        this.mqTarget = mqTarget;
    }
}

