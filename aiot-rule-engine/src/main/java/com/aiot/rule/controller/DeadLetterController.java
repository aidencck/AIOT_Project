package com.aiot.rule.controller;

import com.aiot.rule.service.DeadLetterQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "死信队列运维", description = "设备事件流死信队列查询与重放")
public class DeadLetterController {

    private final DeadLetterQueueService deadLetterQueueService;

    public DeadLetterController(DeadLetterQueueService deadLetterQueueService) {
        this.deadLetterQueueService = deadLetterQueueService;
    }

    @Operation(summary = "查询死信列表", description = "返回最近 N 条死信及当前堆积数量")
    @GetMapping("/dlq")
    public Map<String, Object> listDeadLetters(
            @RequestParam(defaultValue = "100") @Parameter(description = "返回条数") long count) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", deadLetterQueueService.deadLetterCount());
        result.put("deadLetters", deadLetterQueueService.listDeadLetters(count));
        return result;
    }

    @Operation(summary = "重放死信", description = "将指定死信记录的 payload 重新投递回主事件流")
    @PostMapping("/dlq/{recordId}/replay")
    public Map<String, Object> replay(
            @PathVariable @Parameter(description = "死信记录ID") String recordId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", recordId);
        result.put("replayedRecordId", deadLetterQueueService.replay(recordId));
        return result;
    }

    @Operation(summary = "清空死信队列", description = "清空全部死信条目并返回清理数量")
    @DeleteMapping("/dlq")
    public Map<String, Object> purge() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("purgedCount", deadLetterQueueService.purge());
        return result;
    }
}
