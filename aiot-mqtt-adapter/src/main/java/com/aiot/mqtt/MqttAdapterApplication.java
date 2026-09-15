package com.aiot.mqtt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {"com.aiot"})
public class MqttAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(MqttAdapterApplication.class, args);
    }
}
