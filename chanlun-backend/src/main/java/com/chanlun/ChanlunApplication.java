package com.chanlun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 缠论交易分析平台主应用类
 * 
 * @author Chanlun Team
 */
@SpringBootApplication
@EnableScheduling
public class ChanlunApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChanlunApplication.class, args);
    }

}
