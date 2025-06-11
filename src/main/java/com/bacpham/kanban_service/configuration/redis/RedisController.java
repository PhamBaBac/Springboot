package com.bacpham.kanban_service.configuration.redis;
import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/redis")
@RequiredArgsConstructor
public class RedisController {

    private final GenericRedisService<String, String, String> redisService;

    @GetMapping("/get")
    public String getCode(@RequestParam String userId) {
        String code = redisService.get(userId);
        return code != null ? code : "Key not found or expired.";
    }

}
