package com.spa_limited.domain_ssl_checker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BulkSslExpiryResponseItem extends SslExpiryResponse {

	@JsonProperty("client_name")
	private String clientName;

	@JsonProperty("client_ip")
	private String clientIp;

	@JsonProperty("client_domain")
	private String clientDomain;

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
}
