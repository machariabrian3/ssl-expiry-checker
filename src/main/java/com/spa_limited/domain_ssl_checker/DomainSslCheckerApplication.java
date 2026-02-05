package com.spa_limited.domain_ssl_checker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DomainSslCheckerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DomainSslCheckerApplication.class, args);
	}

}
