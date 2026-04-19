package com.aiot.shadow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ShadowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShadowApplication.class, args);
    }
}
