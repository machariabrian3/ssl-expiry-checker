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
		OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
		HandshakeResult result;
		try {
			result = attemptHandshake(host, port, false);
		} catch (Exception firstEx) {
			logger.warn("TLS handshake failed with default trust for {}:{} - {}", host, port, firstEx.getMessage());
			try {
				result = attemptHandshake(host, port, true);
				result.chainTrusted = false;
			} catch (Exception secondEx) {
				String message = friendlyMessage(secondEx);
				logger.warn("TLS handshake failed for {}:{} - {}", host, port, message);
				return SslExpiryResponse.error(host, port, message, checkedAt);
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
		return response;
	}

	private int calculateDaysRemaining(Instant now, Instant notAfter, boolean expired) {
		if (expired) {
			return 0;
		}
		Duration remaining = Duration.between(now, notAfter);
		double days = remaining.toMillis() / (1000.0 * 60 * 60 * 24);
		return (int) Math.ceil(days);
	}

	private HandshakeResult attemptHandshake(String host, int port, boolean trustAll)
			throws IOException, GeneralSecurityException {
		SSLSocketFactory factory = sslContext(trustAll).getSocketFactory();
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), properties.getConnectTimeoutMs());
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
