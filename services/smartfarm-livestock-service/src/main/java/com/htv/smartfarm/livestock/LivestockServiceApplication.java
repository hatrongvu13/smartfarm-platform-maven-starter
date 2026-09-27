package com.htv.smartfarm.livestock;

import com.htv.smartfarm.livestock.task.TaskDeadlineSettings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TaskDeadlineSettings.class)
public class LivestockServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LivestockServiceApplication.class, args);
    }
}
