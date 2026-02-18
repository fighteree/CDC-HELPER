package com.yzdbx.cdc.service;

import com.yzdbx.cdc.mapper.ChangeLogMapper;
import com.yzdbx.cdc.model.entity.ChangeLogEntity;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 变更日志服务：
 * - 提供异步写入能力，避免阻塞 CDC 主流程
 */
@Service
public class ChangeLogService {

    private final ChangeLogMapper changeLogMapper;
    private final ExecutorService executor;

    public ChangeLogService(ChangeLogMapper changeLogMapper) {
        this.changeLogMapper = changeLogMapper;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cdc-change-log-writer");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 异步保存日志。调用方不关心写入结果，只要尽量不拖慢主流程即可。
     */
    public void saveAsync(ChangeLogEntity log) {
        if (log == null) {
            return;
        }
        // 默认重放计数为 0
        if (log.getReplayCount() == null) {
            log.setReplayCount(0);
        }
        executor.submit(() -> changeLogMapper.insert(log));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}

