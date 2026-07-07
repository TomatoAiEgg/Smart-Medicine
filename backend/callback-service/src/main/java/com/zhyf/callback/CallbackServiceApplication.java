package com.zhyf.callback;

import com.zhyf.callback.application.CallbackProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CallbackProperties.class)
public class CallbackServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallbackServiceApplication.class, args);
    }
}
