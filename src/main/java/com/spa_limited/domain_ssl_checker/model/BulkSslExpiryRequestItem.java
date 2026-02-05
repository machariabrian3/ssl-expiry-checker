package com.spa_limited.domain_ssl_checker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class BulkSslExpiryRequestItem {

	@JsonProperty("client_name")
	@NotBlank
	private String clientName;

	@JsonProperty("client_ip")
	private String clientIp;

	@JsonProperty("client_domain")
	@NotBlank
	private String clientDomain;

	@Min(1)
	@Max(65535)
	private Integer port = 443;

	public String getClientName() {
		return clientName;
	}

	public void setClientName(String clientName) {
		this.clientName = clientName;
	}

	public String getClientIp() {
		return clientIp;
	}

	public void setClientIp(String clientIp) {
		this.clientIp = clientIp;
	}

	public String getClientDomain() {
		return clientDomain;
	}

	public void setClientDomain(String clientDomain) {
		this.clientDomain = clientDomain;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}
}
