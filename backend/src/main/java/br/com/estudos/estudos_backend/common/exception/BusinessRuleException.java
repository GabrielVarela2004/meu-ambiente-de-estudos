package br.com.estudos.estudos_backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Operação que viola uma regra de negócio no estado atual do recurso: 409 por padrão
 * (ex.: bloco já ativo) ou 422 quando o conteúdo é válido mas não processável.
 */
public class BusinessRuleException extends ResponseStatusException {

	public BusinessRuleException(String message) {
		super(HttpStatus.CONFLICT, message);
	}

	public static BusinessRuleException unprocessable(String message) {
		return new BusinessRuleException(HttpStatus.UNPROCESSABLE_CONTENT, message);
	}

	private BusinessRuleException(HttpStatus status, String message) {
		super(status, message);
	}
}
