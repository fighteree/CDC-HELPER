package com.yzdbx.cdc.model.entity;

public class MappingFieldEntity {

    private Long id;
    private Long mappingId;
    private String sourceCol;
    private String targetCol;
    private Integer isPk;
    private String expr;
    /**
     * 结果类型：
     * - AUTO: 默认，根据目标列类型自动推断
     * - STRING: 始终按字符串处理
     * - INT: 按整数处理
     * - DECIMAL: 按小数处理
     * - BOOLEAN: 按布尔处理
     * - DATETIME: 按日期时间处理
     */
    private String resultType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public String getSourceCol() {
        return sourceCol;
    }

    public void setSourceCol(String sourceCol) {
        this.sourceCol = sourceCol;
    }

    public String getTargetCol() {
        return targetCol;
    }

    public void setTargetCol(String targetCol) {
        this.targetCol = targetCol;
    }

    public Integer getIsPk() {
        return isPk;
    }

    public void setIsPk(Integer isPk) {
        this.isPk = isPk;
    }

    public String getExpr() {
        return expr;
    }

    public void setExpr(String expr) {
        this.expr = expr;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }
}

