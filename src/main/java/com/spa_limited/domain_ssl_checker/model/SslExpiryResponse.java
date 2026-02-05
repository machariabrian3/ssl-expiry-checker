package com.spa_limited.domain_ssl_checker.model;

import java.time.OffsetDateTime;

public class SslExpiryResponse {

	private String host;
	private int port;
	private OffsetDateTime expiresAt;
	private int daysRemaining;
	private SslExpiryStatus status;
	private String errorMessage;
	private OffsetDateTime checkedAt;
	private Boolean chainTrusted;

	public static SslExpiryResponse error(String host, int port, String message, OffsetDateTime checkedAt) {
		SslExpiryResponse response = new SslExpiryResponse();
		response.host = host;
		response.port = port;
		response.status = SslExpiryStatus.ERROR;
		response.errorMessage = message;
		response.checkedAt = checkedAt;
		response.daysRemaining = 0;
		return response;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(OffsetDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public int getDaysRemaining() {
		return daysRemaining;
	}

	public void setDaysRemaining(int daysRemaining) {
		this.daysRemaining = daysRemaining;
	}

	public SslExpiryStatus getStatus() {
		return status;
	}

	public void setStatus(SslExpiryStatus status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public OffsetDateTime getCheckedAt() {
		return checkedAt;
	}

	public void setCheckedAt(OffsetDateTime checkedAt) {
		this.checkedAt = checkedAt;
	}

	public Boolean getChainTrusted() {
		return chainTrusted;
	}

	public void setChainTrusted(Boolean chainTrusted) {
		this.chainTrusted = chainTrusted;
	}
}
