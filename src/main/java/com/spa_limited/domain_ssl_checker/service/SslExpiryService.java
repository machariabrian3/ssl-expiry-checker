package com.spa_limited.domain_ssl_checker.service;

import com.spa_limited.domain_ssl_checker.config.SslExpiryProperties;
import com.spa_limited.domain_ssl_checker.model.SslExpiryResponse;
import com.spa_limited.domain_ssl_checker.model.SslExpiryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class SslExpiryService {

	private static final Logger logger = LoggerFactory.getLogger(SslExpiryService.class);

	private final SslExpiryProperties properties;

	public SslExpiryService(SslExpiryProperties properties) {
		this.properties = properties;
	}

	public SslExpiryResponse check(String host, int port) {
		CheckResult result = checkInternal(host, port, null);
		return result.response;
	}

	public SslExpiryResponse checkWithFallback(String host, int port, String fallbackIp, boolean resolveDnsIfNoIp) {
		CheckResult primary = checkInternal(host, port, null);
		if (primary.response.getStatus() == SslExpiryStatus.ERROR) {
			return primary.response;
		}
		if (!isLikelyIntercepted(primary.certificate)) {
			return primary.response;
		}

		List<String> fallbackTargets = resolveFallbackTargets(host, fallbackIp, resolveDnsIfNoIp);
		for (String target : fallbackTargets) {
			CheckResult fallback = checkInternal(host, port, target);
			if (fallback.response.getStatus() != SslExpiryStatus.ERROR) {
				return fallback.response;
			}
		}
		return primary.response;
	}

	private CheckResult checkInternal(String host, int port, String connectAddress) {
		OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
		HandshakeResult result;
		try {
			result = attemptHandshake(host, port, connectAddress, false);
		} catch (Exception firstEx) {
			logger.warn("TLS handshake failed with default trust for {}:{} - {}", host, port, firstEx.getMessage());
			try {
				result = attemptHandshake(host, port, connectAddress, true);
				result.chainTrusted = false;
			} catch (Exception secondEx) {
				String message = friendlyMessage(secondEx);
				logger.warn("TLS handshake failed for {}:{} - {}", host, port, message);
				return CheckResult.error(host, port, message, checkedAt);
			}
		}

		Instant notAfter = result.certificate.getNotAfter().toInstant();
		Instant now = checkedAt.toInstant();
		boolean expired = !notAfter.isAfter(now);
		int daysRemaining = calculateDaysRemaining(now, notAfter, expired);

		SslExpiryStatus status;
		if (expired) {
			status = SslExpiryStatus.EXPIRED;
		} else if (daysRemaining <= properties.getExpiringDays()) {
			status = SslExpiryStatus.EXPIRING;
		} else {
			status = SslExpiryStatus.OK;
		}

		SslExpiryResponse response = new SslExpiryResponse();
		response.setHost(host);
		response.setPort(port);
		response.setExpiresAt(OffsetDateTime.ofInstant(notAfter, ZoneOffset.UTC));
		response.setDaysRemaining(daysRemaining);
		response.setStatus(status);
		response.setCheckedAt(checkedAt);
		response.setChainTrusted(result.chainTrusted);
		return new CheckResult(response, result.certificate);
	}

	private int calculateDaysRemaining(Instant now, Instant notAfter, boolean expired) {
		if (expired) {
			return 0;
		}
		Duration remaining = Duration.between(now, notAfter);
		double days = remaining.toMillis() / (1000.0 * 60 * 60 * 24);
		return (int) Math.ceil(days);
	}

	private HandshakeResult attemptHandshake(String host, int port, String connectAddress, boolean trustAll)
			throws IOException, GeneralSecurityException {
		SSLSocketFactory factory = sslContext(trustAll).getSocketFactory();
		String address = (connectAddress == null || connectAddress.isBlank()) ? host : connectAddress;
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(address, port), properties.getConnectTimeoutMs());
			socket.setSoTimeout(properties.getReadTimeoutMs());

			try (SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true)) {
				SSLParameters params = sslSocket.getSSLParameters();
				params.setServerNames(List.of(new SNIHostName(host)));
				sslSocket.setSSLParameters(params);
				sslSocket.startHandshake();
				Certificate[] chain = sslSocket.getSession().getPeerCertificates();
				if (chain == null || chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
					throw new GeneralSecurityException("No X.509 certificate presented by peer");
				}
				return new HandshakeResult((X509Certificate) chain[0], !trustAll);
			}
		}
	}

	private SSLContext sslContext(boolean trustAll) throws GeneralSecurityException {
		SSLContext context = SSLContext.getInstance("TLS");
		if (trustAll) {
			TrustManager[] trustManagers = new TrustManager[] { new TrustAllX509TrustManager() };
			context.init(null, trustManagers, null);
		} else {
			context.init(null, null, null);
		}
		return context;
	}

	private String friendlyMessage(Exception ex) {
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return ex.getClass().getSimpleName();
		}
		return ex.getClass().getSimpleName() + ": " + message;
	}

	private static class HandshakeResult {
		private final X509Certificate certificate;
		private boolean chainTrusted;

		private HandshakeResult(X509Certificate certificate, boolean chainTrusted) {
			this.certificate = certificate;
			this.chainTrusted = chainTrusted;
		}
	}

	private static class CheckResult {
		private final SslExpiryResponse response;
		private final X509Certificate certificate;

		private CheckResult(SslExpiryResponse response, X509Certificate certificate) {
			this.response = response;
			this.certificate = certificate;
		}

		private static CheckResult error(String host, int port, String message, OffsetDateTime checkedAt) {
			return new CheckResult(SslExpiryResponse.error(host, port, message, checkedAt), null);
		}
	}

	private boolean isLikelyIntercepted(X509Certificate certificate) {
		if (certificate == null) {
			return false;
		}
		String subject = certificate.getSubjectX500Principal().getName();
		String issuer = certificate.getIssuerX500Principal().getName();
		String haystack = (subject + " " + issuer).toLowerCase();
		return haystack.contains("fortinet") || haystack.contains("blocked page");
	}

	private List<String> resolveFallbackTargets(String host, String fallbackIp, boolean resolveDnsIfNoIp) {
		List<String> targets = new java.util.ArrayList<>();
		if (fallbackIp != null && !fallbackIp.isBlank()) {
			targets.add(fallbackIp.trim());
			return targets;
		}
		if (!resolveDnsIfNoIp) {
			return targets;
		}
		try {
			java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(host);
			List<String> ipv4 = new java.util.ArrayList<>();
			List<String> ipv6 = new java.util.ArrayList<>();
			for (java.net.InetAddress address : addresses) {
				String ip = address.getHostAddress();
				if (address instanceof java.net.Inet4Address) {
					ipv4.add(ip);
				} else {
					ipv6.add(ip);
				}
			}
			targets.addAll(ipv4);
			targets.addAll(ipv6);
		} catch (Exception ex) {
			logger.warn("Failed to resolve fallback IPs for {} - {}", host, ex.getMessage());
		}
		return targets;
	}

	private static class TrustAllX509TrustManager implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}
}
