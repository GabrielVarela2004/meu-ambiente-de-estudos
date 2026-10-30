package br.com.estudos.estudos_backend.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Converte o estouro do limite de multipart (antes de chegar ao controller) em 413 com mensagem em português.
 * Sem isto, o frontend só recebe "HTTP 413" sem explicação.
 */
@RestControllerAdvice
public class UploadExceptionHandler {

	private final DataSize maxFileSize;

	public UploadExceptionHandler(@Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		HttpStatus status = HttpStatus.CONTENT_TOO_LARGE;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", "O arquivo excede o tamanho máximo permitido de " + maxFileSize.toMegabytes()
				+ " MB. Divida o arquivo ou reduza o tamanho antes de enviar.");
		body.put("timestamp", Instant.now().toString());
		return ResponseEntity.status(status).body(body);
	}
}
