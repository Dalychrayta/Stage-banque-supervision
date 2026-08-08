package com.bct.healing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class AutoHealingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoHealingServiceApplication.class, args);
    }
}
