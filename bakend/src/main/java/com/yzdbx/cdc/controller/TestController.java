package com.yzdbx.cdc.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @BelongsProject: bakend
 * @BelongsPackage: com.yzdbx.cdc.controller
 * @Author: lk
 * @CreateTime: 2026-02-18
 * @Description: TODO
 * @Version: 1.0
 */
@RestController
public class TestController {


    @PostMapping("/test")
    public String test(@RequestBody Object payload){
        return "hello world";
    }
}
