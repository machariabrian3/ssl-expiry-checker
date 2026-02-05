package com.spa_limited.domain_ssl_checker.controller;

import com.spa_limited.domain_ssl_checker.model.SslExpiryRequest;
import com.spa_limited.domain_ssl_checker.model.SslExpiryResponse;
import com.spa_limited.domain_ssl_checker.model.BulkSslExpiryRequestItem;
import com.spa_limited.domain_ssl_checker.model.BulkSslExpiryResponseItem;
import com.spa_limited.domain_ssl_checker.service.SslExpiryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/ssl")
public class SslExpiryController {

	private final SslExpiryService sslExpiryService;

	public SslExpiryController(SslExpiryService sslExpiryService) {
		this.sslExpiryService = sslExpiryService;
	}

	@GetMapping(value = "/expiry", produces = MediaType.APPLICATION_JSON_VALUE)
	public SslExpiryResponse getExpiry(
			@RequestParam("host") @NotBlank String host,
			@RequestParam(value = "port", defaultValue = "443") @Min(1) @Max(65535) int port
	) {
		return sslExpiryService.check(host.trim(), port);
	}

	@PostMapping(value = "/expiry", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public SslExpiryResponse postExpiry(@Valid @RequestBody SslExpiryRequest request) {
		String host = request.getHost() == null ? "" : request.getHost().trim();
		int port = request.getPort() == null ? 443 : request.getPort();
		return sslExpiryService.check(host, port);
	}

	@PostMapping(value = "/expiry/bulk", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public List<BulkSslExpiryResponseItem> bulkExpiry(@Valid @RequestBody List<@Valid BulkSslExpiryRequestItem> items) {
		List<BulkSslExpiryResponseItem> results = new ArrayList<>();
		for (BulkSslExpiryRequestItem item : items) {
			String host = item.getClientDomain() == null ? "" : item.getClientDomain().trim();
			int port = item.getPort() == null ? 443 : item.getPort();
			SslExpiryResponse response = sslExpiryService.check(host, port);
			BulkSslExpiryResponseItem enriched = new BulkSslExpiryResponseItem();
			enriched.setClientName(item.getClientName());
			enriched.setClientIp(item.getClientIp());
			enriched.setClientDomain(item.getClientDomain());
			enriched.setHost(response.getHost());
			enriched.setPort(response.getPort());
			enriched.setExpiresAt(response.getExpiresAt());
			enriched.setDaysRemaining(response.getDaysRemaining());
			enriched.setStatus(response.getStatus());
			enriched.setErrorMessage(response.getErrorMessage());
			enriched.setCheckedAt(response.getCheckedAt());
			enriched.setChainTrusted(response.getChainTrusted());
			results.add(enriched);
		}
		return results;
	}
}
