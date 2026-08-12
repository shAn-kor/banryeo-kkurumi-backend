package com.banryeokkurumi;

import org.springframework.boot.SpringApplication;

public class TestBanryeoKkurumiBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(BanryeoKkurumiBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
