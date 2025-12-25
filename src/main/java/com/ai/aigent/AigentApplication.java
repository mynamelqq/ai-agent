package com.ai.aigent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties  // 如果需要
public class AigentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AigentApplication.class, args);
    }

}
