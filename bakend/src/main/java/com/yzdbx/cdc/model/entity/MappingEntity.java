package com.yzdbx.cdc.model.entity;

public class MappingEntity {

    private Long id;
    private String name;
    private Long sourceDataSourceId;
    private String sourceSchema;
    private String sourceTable;
    private String sourceKafkaServers;
    private String targetType;
    private Long targetDataSourceId;
    private String targetSchema;
    private String targetTable;
    private Integer enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSourceDataSourceId() {
        return sourceDataSourceId;
    }

    public void setSourceDataSourceId(Long sourceDataSourceId) {
        this.sourceDataSourceId = sourceDataSourceId;
    }

    public String getSourceSchema() {
        return sourceSchema;
    }

    public void setSourceSchema(String sourceSchema) {
        this.sourceSchema = sourceSchema;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getSourceKafkaServers() {
        return sourceKafkaServers;
    }

    public void setSourceKafkaServers(String sourceKafkaServers) {
        this.sourceKafkaServers = sourceKafkaServers;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetDataSourceId() {
        return targetDataSourceId;
    }

    public void setTargetDataSourceId(Long targetDataSourceId) {
        this.targetDataSourceId = targetDataSourceId;
    }

    public String getTargetSchema() {
        return targetSchema;
    }

    public void setTargetSchema(String targetSchema) {
        this.targetSchema = targetSchema;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}

