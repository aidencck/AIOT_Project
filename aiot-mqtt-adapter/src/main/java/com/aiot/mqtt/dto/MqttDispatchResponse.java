package com.aiot.mqtt.dto;

public class MqttDispatchResponse {
    private String parserEventId;
    private String parserEventType;
    private String parserStreamKey;
    private String deviceId;

    public String getParserEventId() {
        return parserEventId;
    }

    public void setParserEventId(String parserEventId) {
        this.parserEventId = parserEventId;
    }

    public String getParserEventType() {
        return parserEventType;
    }

    public void setParserEventType(String parserEventType) {
        this.parserEventType = parserEventType;
    }

    public String getParserStreamKey() {
        return parserStreamKey;
    }

    public void setParserStreamKey(String parserStreamKey) {
        this.parserStreamKey = parserStreamKey;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
