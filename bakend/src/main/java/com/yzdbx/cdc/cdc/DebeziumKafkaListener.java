package com.yzdbx.cdc.cdc;

import org.springframework.stereotype.Component;

/**
 * 说明：
 * 早期版本通过 @KafkaListener 全局监听 Debezium Topic。
 * 现在已经改为由 {@link CdcConsumerManager} 按映射配置动态创建 Kafka Consumer，
 * 因此这里保留一个空的占位组件，避免误注册全局监听器。
 */
@Component
public class DebeziumKafkaListener {
    // 不再使用 @KafkaListener，实际消费逻辑已迁移到 CdcConsumerManager
}

