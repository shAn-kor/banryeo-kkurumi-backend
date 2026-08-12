package com.banryeokkurumi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BanryeoKkurumiBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BanryeoKkurumiBackendApplication.class, args);
	}

}
