package br.com.estudos.estudos_backend.security;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolve e valida o identificador do usuário atual da requisição para garantir isolamento
 * de blocos, fontes, conversas, questões, exercícios e histórico de aprendizagem.
 *
 * Quando o cabeçalho {@code X-Study-User-Id} não é enviado (ex.: chamadas locais de desenvolvimento
 * ou testes legados), utiliza o identificador padrão {@code default-user}.
 */
@Component
public class CurrentUserResolver {

	public static final String USER_HEADER = "X-Study-User-Id";
	public static final String DEFAULT_USER_ID = "default-user";
	private static final Pattern VALID_USER_ID = Pattern.compile("^[a-zA-Z0-9._@-]{1,120}$");

	private static final ThreadLocal<String> OVERRIDE_USER = new ThreadLocal<>();

	public String currentUserId() {
		String override = OVERRIDE_USER.get();
		if (override != null && !override.isBlank()) {
			return validateUserId(override);
		}
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes servletAttributes) {
			HttpServletRequest request = servletAttributes.getRequest();
			String headerValue = request.getHeader(USER_HEADER);
			if (headerValue != null && !headerValue.isBlank()) {
				return validateUserId(headerValue.trim());
			}
		}
		return DEFAULT_USER_ID;
	}

	public static String validateUserId(String rawUserId) {
		if (rawUserId == null || rawUserId.isBlank()) {
			return DEFAULT_USER_ID;
		}
		String trimmed = rawUserId.trim();
		if (!VALID_USER_ID.matcher(trimmed).matches()) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Invalid user identifier in X-Study-User-Id header");
		}
		return trimmed;
	}

	public void setOverrideUser(String userId) {
		if (userId == null) {
			OVERRIDE_USER.remove();
		} else {
			OVERRIDE_USER.set(validateUserId(userId));
		}
	}

	public void clearOverrideUser() {
		OVERRIDE_USER.remove();
	}
}
