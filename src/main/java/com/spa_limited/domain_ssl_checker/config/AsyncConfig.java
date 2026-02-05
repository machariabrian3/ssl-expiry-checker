package com.spa_limited.domain_ssl_checker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

	@Bean(destroyMethod = "shutdown")
	public ExecutorService sslBulkExecutor(SslExpiryProperties properties) {
		return Executors.newFixedThreadPool(properties.getBulkConcurrency());
	}
}
