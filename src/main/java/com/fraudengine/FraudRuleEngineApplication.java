package com.fraudengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudRuleEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(FraudRuleEngineApplication.class, args);
	}

}
