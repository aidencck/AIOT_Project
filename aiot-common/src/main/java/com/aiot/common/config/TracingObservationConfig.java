package com.aiot.common.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * 观测采样判定：排除 /actuator/** 端点的 HTTP 观测采样，避免 Tempo 中被
 * /actuator/health、/actuator/prometheus 等探活/监控请求的 trace 污染。
 */
@Configuration
public class TracingObservationConfig {

    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";
    private static final String URI_KEY = "uri";
    private static final String ACTUATOR_PREFIX = "/actuator";

    public TracingObservationConfig(ObservationRegistry registry) {
        registry.observationConfig().observationPredicate((name, context) -> {
            if (!HTTP_SERVER_REQUESTS.equals(name)) {
                return true;
            }
            return !isActuatorEndpoint(context);
        });
    }

    private boolean isActuatorEndpoint(Observation.Context context) {
        for (KeyValue keyValue : context.getAllKeyValues()) {
            if (URI_KEY.equals(keyValue.getKey())) {
                String uri = keyValue.getValue();
                return uri != null && uri.startsWith(ACTUATOR_PREFIX);
            }
        }
        return false;
    }
}
