package br.com.estudos.estudos_backend.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Converte toda exceção da API no formato único {@link ApiErrorResponse}, com mensagens em português.
 * Erros inesperados viram 500 com mensagem genérica: o stack trace fica só no log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String VALIDATION_MESSAGE = "Há campos com valores inválidos.";

	private final DataSize maxFileSize;

	public GlobalExceptionHandler(@Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ex.getBindingResult().getGlobalErrors()
				.forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
		return build(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, fields);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidParameters(HandlerMethodValidationException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getParameterValidationResults().forEach(result -> {
			String name = result.getMethodParameter().getParameterName();
			String key = name == null ? "parametro" : name;
			if (result.getContainerIndex() != null) {
				key += "[" + result.getContainerIndex() + "]";
			}
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				String field = error instanceof FieldError fieldError ? key + "." + fieldError.getField() : key;
				fields.putIfAbsent(field, error.getDefaultMessage());
			}
		});
		return build(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, fields);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
		return build(HttpStatus.BAD_REQUEST, "O corpo da requisição está ausente ou mal formatado.", null);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
		return build(HttpStatus.BAD_REQUEST, "O parâmetro obrigatório '" + ex.getParameterName() + "' não foi informado.",
				Map.of(ex.getParameterName(), "obrigatório"));
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
		return build(HttpStatus.BAD_REQUEST, "A parte obrigatória '" + ex.getRequestPartName() + "' não foi enviada.",
				Map.of(ex.getRequestPartName(), "obrigatório"));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return build(HttpStatus.BAD_REQUEST, "O valor informado para '" + ex.getName() + "' é inválido.",
				Map.of(ex.getName(), "valor inválido"));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		return build(HttpStatus.CONTENT_TOO_LARGE, "O arquivo excede o tamanho máximo permitido de "
				+ maxFileSize.toMegabytes() + " MB. Divida o arquivo ou reduza o tamanho antes de enviar.", null);
	}

	/** Exceções do domínio (NotFound, BusinessRule, InvalidInput) e demais ResponseStatusException. */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
		int status = ex.getStatusCode().value();
		if (status >= 500) {
			log.warn("Erro {} na requisição: {}", status, ex.getReason());
		}
		String reason = ex.getReason();
		return build(status, reason == null || reason.isBlank() ? ErrorMessages.defaultMessage(status) : reason, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
		if (ex instanceof ErrorResponse errorResponse) {
			// Exceções do próprio Spring MVC (rota inexistente, método não permitido, mídia não suportada…).
			int status = errorResponse.getStatusCode().value();
			return build(status, ErrorMessages.defaultMessage(status), null);
		}
		log.error("Erro inesperado na requisição", ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessages.defaultMessage(500), null);
	}

	private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, Map<String, String> fields) {
		return build(status.value(), message, fields);
	}

	private static ResponseEntity<ApiErrorResponse> build(int status, String message, Map<String, String> fields) {
		return ResponseEntity.status(status).body(ApiErrorResponse.of(status, message, fields));
	}
}
