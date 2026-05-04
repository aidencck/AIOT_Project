package com.aiot.shadow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.aiot"})
@EnableDiscoveryClient
@EnableScheduling
public class ShadowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShadowApplication.class, args);
    }
}
