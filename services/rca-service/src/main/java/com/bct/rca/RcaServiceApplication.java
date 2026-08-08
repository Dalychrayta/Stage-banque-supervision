package com.bct.rca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class RcaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RcaServiceApplication.class, args);
    }
}
