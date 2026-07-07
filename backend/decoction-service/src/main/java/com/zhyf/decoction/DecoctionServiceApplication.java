package com.zhyf.decoction;

import com.zhyf.decoction.config.DecoctionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DecoctionProperties.class)
public class DecoctionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecoctionServiceApplication.class, args);
    }
}
