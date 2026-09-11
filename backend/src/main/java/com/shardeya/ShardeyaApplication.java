package com.shardeya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShardeyaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShardeyaApplication.class, args);
    }
}
