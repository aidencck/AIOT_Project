package com.aiot.rule.service;

import com.aiot.common.event.DeviceEvent;
import com.aiot.rule.model.RuleDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class RuleActionExecutor {

    private final ObjectMapper objectMapper;
    private final OpsClosureService opsClosureService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${aiot.rule.action.webhook.timeout-ms:2000}")
    private long webhookTimeoutMs;

    public RuleActionExecutor(ObjectMapper objectMapper, OpsClosureService opsClosureService) {
        this.objectMapper = objectMapper;
        this.opsClosureService = opsClosureService;
    }

    public void execute(RuleDefinition rule, DeviceEvent event) {
        String actionType = StringUtils.hasText(rule.getActionType()) ? rule.getActionType().trim().toUpperCase() : "ALERT_LOG";
        if ("WEBHOOK_POST".equals(actionType)) {
            executeWebhook(rule, event);
            return;
        }
        if ("ALARM_CREATE".equals(actionType)) {
            executeAlarmCreate(rule, event);
            return;
        }
        executeAlertLog(rule, event);
    }

    private void executeAlertLog(RuleDefinition rule, DeviceEvent event) {
        log.info("Rule action ALERT_LOG, ruleId={}, eventId={}, eventType={}, deviceId={}, payload={}",
                rule.getRuleId(), event.getEventId(), event.getEventType(), event.getDeviceId(), rule.getActionPayload());
        // Backward compatible: 旧规则未声明 ALARM_CREATE 时，也自动进入告警-工单闭环。
        opsClosureService.createAlarmAndWorkOrder(rule, event, rule.getActionPayload());
    }

    private void executeAlarmCreate(RuleDefinition rule, DeviceEvent event) {
        opsClosureService.createAlarmAndWorkOrder(rule, event, rule.getActionPayload());
        log.info("Rule action ALARM_CREATE done, ruleId={}, eventId={}, eventType={}, deviceId={}",
                rule.getRuleId(), event.getEventId(), event.getEventType(), event.getDeviceId());
    }

    private void executeWebhook(RuleDefinition rule, DeviceEvent event) {
        String rawPayload = rule.getActionPayload();
        if (!StringUtils.hasText(rawPayload)) {
            log.warn("Rule WEBHOOK_POST skipped: empty payload, ruleId={}", rule.getRuleId());
            return;
        }
        String url = extractUrl(rawPayload);
        if (!StringUtils.hasText(url)) {
            log.warn("Rule WEBHOOK_POST skipped: invalid payload url, ruleId={}", rule.getRuleId());
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("ruleId", rule.getRuleId());
            body.put("event", event);
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(webhookTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Rule action WEBHOOK_POST success, ruleId={}, eventId={}, status={}",
                        rule.getRuleId(), event.getEventId(), response.statusCode());
            } else {
                log.warn("Rule action WEBHOOK_POST failed, ruleId={}, eventId={}, status={}, body={}",
                        rule.getRuleId(), event.getEventId(), response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Rule action WEBHOOK_POST error, ruleId={}, eventId={}", rule.getRuleId(), event.getEventId(), e);
        }
    }

    private String extractUrl(String actionPayload) {
        String payload = actionPayload.trim();
        if (payload.startsWith("{")) {
            try {
                Map<?, ?> map = objectMapper.readValue(payload, Map.class);
                Object url = map.get("url");
                return url == null ? null : String.valueOf(url);
            } catch (Exception e) {
                return null;
            }
        }
        return payload;
    }
}
