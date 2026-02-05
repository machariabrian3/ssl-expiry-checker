package com.spa_limited.domain_ssl_checker.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ssl")
public class SslExpiryProperties {

	@Min(100)
	private int connectTimeoutMs = 5000;

	@Min(100)
	private int readTimeoutMs = 7000;

	@Min(1)
	private int expiringDays = 7;

	@Min(1)
	private int bulkConcurrency = 16;

	@Min(100)
	private int bulkTimeoutMs = 180000;

	public int getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(int connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public int getReadTimeoutMs() {
		return readTimeoutMs;
	}

	public void setReadTimeoutMs(int readTimeoutMs) {
		this.readTimeoutMs = readTimeoutMs;
	}

	public int getExpiringDays() {
		return expiringDays;
	}

	public void setExpiringDays(int expiringDays) {
		this.expiringDays = expiringDays;
	}

	public int getBulkConcurrency() {
		return bulkConcurrency;
	}

	public void setBulkConcurrency(int bulkConcurrency) {
		this.bulkConcurrency = bulkConcurrency;
	}

	public int getBulkTimeoutMs() {
		return bulkTimeoutMs;
	}

	public void setBulkTimeoutMs(int bulkTimeoutMs) {
		this.bulkTimeoutMs = bulkTimeoutMs;
	}
}
