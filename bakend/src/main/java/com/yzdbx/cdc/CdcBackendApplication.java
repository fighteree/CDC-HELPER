package com.yzdbx.cdc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
*@BelongsProject: bakend
*@BelongsPackage: com.yzdbx.cdc
*@Author: lk
*@CreateTime: 2026-02-18
*@Description: TODO
*@Version: 1.0
*/
@SpringBootApplication(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
@EnableScheduling
public class CdcBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdcBackendApplication.class,args);
    }
}
