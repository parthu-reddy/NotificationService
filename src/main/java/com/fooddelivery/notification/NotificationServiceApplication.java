package com.fooddelivery.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication(scanBasePackages = {"com.fooddelivery.notification", "com.fooddelivery.common"})
@EntityScan(basePackages = {"com.fooddelivery.notification", "com.fooddelivery.common"})
@EnableJpaRepositories(basePackages = {"com.fooddelivery.notification", "com.fooddelivery.common"})
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
