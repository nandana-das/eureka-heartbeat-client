package com.covacsis.ipf.pinot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RegisterPinotApplication {

	public static void main(String[] args) {
		SpringApplication.run(RegisterPinotApplication.class, args);
	}

}