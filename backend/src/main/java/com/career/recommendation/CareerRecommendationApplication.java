package com.career.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CareerRecommendationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CareerRecommendationApplication.class, args);
    }
}
