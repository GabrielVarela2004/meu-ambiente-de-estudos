package br.com.estudos.estudos_backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recurso inexistente ou de outro usuário (404).
 * Estende {@link ResponseStatusException} para manter os {@code catch} e os testes existentes.
 */
public class NotFoundException extends ResponseStatusException {

	public NotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, message);
	}
}
