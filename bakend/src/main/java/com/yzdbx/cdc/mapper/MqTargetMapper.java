package com.yzdbx.cdc.mapper;

import com.yzdbx.cdc.model.entity.MqTargetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MqTargetMapper {

    MqTargetEntity selectByMappingId(@Param("mappingId") Long mappingId);

    int insert(MqTargetEntity entity);

    int update(MqTargetEntity entity);

    int deleteByMappingId(@Param("mappingId") Long mappingId);
}

