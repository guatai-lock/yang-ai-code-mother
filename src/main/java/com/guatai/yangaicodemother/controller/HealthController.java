package com.guatai.yangaicodemother.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ClassName: a
 * Package: com.guatai.yangaicodemother.controller
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午3:10
 * @Version 1.0
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public String healthCheck() {
        return "ok";
    }
}

