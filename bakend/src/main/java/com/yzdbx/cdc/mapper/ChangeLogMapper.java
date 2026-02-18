package com.yzdbx.cdc.mapper;

import com.yzdbx.cdc.model.entity.ChangeLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChangeLogMapper {

    int insert(ChangeLogEntity entity);

    List<ChangeLogEntity> selectRecent(@Param("limit") int limit);

    List<ChangeLogEntity> selectByCondition(
            @Param("mappingId") Long mappingId,
            @Param("targetType") String targetType,
            @Param("success") Integer success,
            @Param("operation") String operation,
            @Param("keyword") String keyword,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int countByCondition(
            @Param("mappingId") Long mappingId,
            @Param("targetType") String targetType,
            @Param("success") Integer success,
            @Param("operation") String operation,
            @Param("keyword") String keyword,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    ChangeLogEntity selectById(@Param("id") Long id);

    int increaseReplayCount(@Param("id") Long id);

    ChangeLogEntity selectLastErrorByMapping(@Param("mappingId") Long mappingId);
}

