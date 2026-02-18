package com.yzdbx.cdc.mapper;

import com.yzdbx.cdc.model.entity.DataSourceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataSourceMapper {

    int insert(DataSourceEntity entity);

    int update(DataSourceEntity entity);

    int deleteById(@Param("id") Long id);

    DataSourceEntity selectById(@Param("id") Long id);

    List<DataSourceEntity> selectAll(@Param("keyword") String keyword,
                                     @Param("dbType") String dbType,
                                     @Param("status") Integer status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword,
                         @Param("dbType") String dbType,
                         @Param("status") Integer status);
}

