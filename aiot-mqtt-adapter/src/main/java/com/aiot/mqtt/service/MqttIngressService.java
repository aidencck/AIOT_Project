package com.aiot.mqtt.service;

import com.aiot.mqtt.dto.MqttDispatchResponse;
import com.aiot.mqtt.dto.MqttIngressRequest;

public interface MqttIngressService {
    MqttDispatchResponse dispatchToParser(MqttIngressRequest request);
}
