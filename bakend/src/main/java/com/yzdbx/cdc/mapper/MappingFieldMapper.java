package com.yzdbx.cdc.mapper;

import com.yzdbx.cdc.model.entity.MappingFieldEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MappingFieldMapper {

    int bulkInsert(@Param("list") List<MappingFieldEntity> list);

    int deleteByMappingId(@Param("mappingId") Long mappingId);

    List<MappingFieldEntity> selectByMappingId(@Param("mappingId") Long mappingId);
}

