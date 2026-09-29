package com.study.chat.common.web;

import com.study.chat.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of("status", "UP", "time", Instant.now().toString()));
    }
}
