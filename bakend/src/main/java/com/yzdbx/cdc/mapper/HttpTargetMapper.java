package com.yzdbx.cdc.mapper;

import com.yzdbx.cdc.model.entity.HttpTargetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HttpTargetMapper {

    HttpTargetEntity selectByMappingId(@Param("mappingId") Long mappingId);

    int insert(HttpTargetEntity entity);

    int update(HttpTargetEntity entity);

    int deleteByMappingId(@Param("mappingId") Long mappingId);
}

