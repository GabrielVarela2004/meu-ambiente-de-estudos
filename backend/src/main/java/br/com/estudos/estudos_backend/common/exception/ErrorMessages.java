package br.com.estudos.estudos_backend.common.exception;

/** Títulos e mensagens padrão em português, usados quando a exceção não traz mensagem própria. */
final class ErrorMessages {

	private ErrorMessages() {
	}

	static String title(int status) {
		return switch (status) {
			case 400 -> "Requisição inválida";
			case 401 -> "Não autenticado";
			case 403 -> "Acesso negado";
			case 404 -> "Não encontrado";
			case 405 -> "Método não permitido";
			case 409 -> "Conflito";
			case 413 -> "Conteúdo muito grande";
			case 415 -> "Formato não suportado";
			case 422 -> "Conteúdo não processável";
			case 429 -> "Muitas requisições";
			case 500 -> "Erro interno";
			case 502 -> "Falha no serviço externo";
			case 503 -> "Serviço indisponível";
			case 504 -> "Tempo esgotado";
			default -> status >= 500 ? "Erro no servidor" : "Erro na requisição";
		};
	}

	static String defaultMessage(int status) {
		return switch (status) {
			case 400 -> "A requisição contém dados inválidos.";
			case 404 -> "O recurso solicitado não foi encontrado.";
			case 405 -> "Este método HTTP não é aceito nesta rota.";
			case 409 -> "A operação conflita com o estado atual do recurso.";
			case 413 -> "O conteúdo enviado excede o tamanho máximo permitido.";
			case 415 -> "O formato do conteúdo enviado não é suportado.";
			case 422 -> "Não foi possível processar o conteúdo enviado.";
			case 429 -> "Limite de requisições excedido. Aguarde alguns instantes e tente novamente.";
			case 502 -> "O serviço externo retornou uma resposta inválida.";
			case 503 -> "O serviço está temporariamente indisponível.";
			case 504 -> "O serviço externo demorou demais para responder.";
			default -> status >= 500
					? "Ocorreu um erro inesperado. Tente novamente mais tarde."
					: "Não foi possível concluir a requisição.";
		};
	}
}
