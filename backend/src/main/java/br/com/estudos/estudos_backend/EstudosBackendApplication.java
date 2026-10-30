package br.com.estudos.estudos_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EstudosBackendApplication {

	public static void main(String[] args) {
		useWindowsTrustStoreWhenAvailable();
		SpringApplication.run(EstudosBackendApplication.class, args);
	}

	/**
	 * No Windows, valida HTTPS com os certificados do sistema operacional em vez do cacerts do Java.
	 * Isso inclui as autoridades públicas e também certificados corporativos ou de VPN/proxy instalados
	 * no Windows (ex.: Cloudflare WARP), sem os quais as chamadas às IAs, ao YouTube e às páginas web falham.
	 * Desligue com USE_WINDOWS_TRUSTSTORE=false ou definindo javax.net.ssl.trustStoreType manualmente.
	 */
	static void useWindowsTrustStoreWhenAvailable() {
		boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
		boolean disabled = "false".equalsIgnoreCase(System.getenv("USE_WINDOWS_TRUSTSTORE"));
		if (windows && !disabled && System.getProperty("javax.net.ssl.trustStoreType") == null) {
			System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
		}
	}
}
