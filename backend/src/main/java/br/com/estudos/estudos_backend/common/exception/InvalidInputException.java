package br.com.estudos.estudos_backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dado de entrada inválido detectado no service (400). Validações de DTO usam Bean Validation.
 */
public class InvalidInputException extends ResponseStatusException {

	public InvalidInputException(String message) {
		super(HttpStatus.BAD_REQUEST, message);
	}
}
