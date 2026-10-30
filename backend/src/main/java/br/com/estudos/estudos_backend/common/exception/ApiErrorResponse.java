package br.com.estudos.estudos_backend.common.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Formato único de erro da API. {@code fields} só vem preenchido em erros de validação.
 */
public record ApiErrorResponse(int status, String error, String message, Map<String, String> fields,
		String timestamp) {

	static ApiErrorResponse of(int status, String message, Map<String, String> fields) {
		return new ApiErrorResponse(status, ErrorMessages.title(status), message, fields, Instant.now().toString());
	}
}
