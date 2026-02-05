package com.spa_limited.domain_ssl_checker.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class SslExpiryRequest {

	@NotBlank
	private String host;

	@Min(1)
	@Max(65535)
	private Integer port = 443;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}
}
