package com.gongwen.assistant.health;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.ok(new HealthStatus("ok", "gongwen-assistant", OffsetDateTime.now()));
    }

    public record HealthStatus(String status, String service, OffsetDateTime checkedAt) {
    }
}
