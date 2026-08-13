package com.liquilabs.vankoo.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class VankooFinanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VankooFinanceServiceApplication.class, args);
    }

}
