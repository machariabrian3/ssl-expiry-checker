package com.spa_limited.domain_ssl_checker.controller;

import com.spa_limited.domain_ssl_checker.model.SslExpiryRequest;
import com.spa_limited.domain_ssl_checker.model.SslExpiryResponse;
import com.spa_limited.domain_ssl_checker.model.BulkSslExpiryRequestItem;
import com.spa_limited.domain_ssl_checker.model.BulkSslExpiryResponseItem;
import com.spa_limited.domain_ssl_checker.config.SslExpiryProperties;
import com.spa_limited.domain_ssl_checker.service.SslExpiryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@Validated
@RequestMapping("/api/v1/ssl")
public class SslExpiryController {

	private final SslExpiryService sslExpiryService;
	private final ExecutorService sslBulkExecutor;
	private final SslExpiryProperties properties;

	public SslExpiryController(SslExpiryService sslExpiryService, ExecutorService sslBulkExecutor,
			SslExpiryProperties properties) {
		this.sslExpiryService = sslExpiryService;
		this.sslBulkExecutor = sslBulkExecutor;
		this.properties = properties;
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
	public List<BulkSslExpiryResponseItem> bulkExpiry(
			@Valid @Size(max = 300) @RequestBody List<@Valid BulkSslExpiryRequestItem> items
	) {
		List<CompletableFuture<BulkSslExpiryResponseItem>> futures = new ArrayList<>();
		for (BulkSslExpiryRequestItem item : items) {
			futures.add(CompletableFuture.supplyAsync(() -> {
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
				return enriched;
			}, sslBulkExecutor));
		}

		long timeoutMs = properties.getBulkTimeoutMs();
		CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		try {
			all.get(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (Exception ex) {
			// Continue returning whatever has completed; unfinished tasks will return ERROR below.
		}

		List<BulkSslExpiryResponseItem> results = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			BulkSslExpiryRequestItem item = items.get(i);
			CompletableFuture<BulkSslExpiryResponseItem> future = futures.get(i);
			if (future.isDone() && !future.isCompletedExceptionally()) {
				results.add(future.join());
			} else {
				BulkSslExpiryResponseItem fallback = new BulkSslExpiryResponseItem();
				fallback.setClientName(item.getClientName());
				fallback.setClientIp(item.getClientIp());
				fallback.setClientDomain(item.getClientDomain());
				fallback.setHost(item.getClientDomain());
				fallback.setPort(item.getPort() == null ? 443 : item.getPort());
				SslExpiryResponse error = SslExpiryResponse.error(
						item.getClientDomain(),
						item.getPort() == null ? 443 : item.getPort(),
						"Timed out while performing SSL check",
						java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
				);
				fallback.setExpiresAt(error.getExpiresAt());
				fallback.setDaysRemaining(error.getDaysRemaining());
				fallback.setStatus(error.getStatus());
				fallback.setErrorMessage(error.getErrorMessage());
				fallback.setCheckedAt(error.getCheckedAt());
				fallback.setChainTrusted(error.getChainTrusted());
				results.add(fallback);
			}
		}
		return results;
	}
}
