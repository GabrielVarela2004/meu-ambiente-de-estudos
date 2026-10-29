package br.com.estudos.estudos_backend.career.adapter.out.jobprovider;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class CareerHttpClientFactory {

	private CareerHttpClientFactory() {
	}

	public static HttpClient create() {
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[]{new X509TrustManager() {
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) {}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) {}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			}}, new SecureRandom());

			return HttpClient.newBuilder()
					.sslContext(sslContext)
					.connectTimeout(Duration.ofSeconds(10))
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build();
		} catch (Exception e) {
			return HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build();
		}
	}
}
