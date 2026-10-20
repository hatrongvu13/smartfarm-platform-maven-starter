package com.htv.smartfarm.order.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderTimeConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
