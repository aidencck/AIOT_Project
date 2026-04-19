package com.aiot.mqtt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MqttAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(MqttAdapterApplication.class, args);
    }
}
