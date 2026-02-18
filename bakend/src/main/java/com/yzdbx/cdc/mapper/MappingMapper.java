package com.yzdbx.cdc.mapper;

import com.yzdbx.cdc.model.entity.MappingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MappingMapper {

    int insert(MappingEntity entity);

    int update(MappingEntity entity);

    int deleteById(@Param("id") Long id);

    MappingEntity selectById(@Param("id") Long id);

    List<MappingEntity> selectAll(@Param("keyword") String keyword,
                                  @Param("targetType") String targetType,
                                  @Param("enabled") Integer enabled,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword,
                         @Param("targetType") String targetType,
                         @Param("enabled") Integer enabled);

    List<MappingEntity> selectEnabledForCdc();

    MappingEntity selectBySource(@Param("schema") String schema,
                                 @Param("table") String table);
}

