package br.com.estudos.estudos_backend.common.exception;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Formato único { status, error, message, fields, timestamp } para cada tipo de erro,
 * testado com MockMvc avulso (sem subir a aplicação).
 */
class GlobalExceptionHandlerTest {

	record Payload(@NotBlank String name) {}

	@RestController
	static class FailingController {
		@GetMapping("/not-found")
		void notFound() {
			throw new NotFoundException("Bloco de estudo não encontrado.");
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new BusinessRuleException("O bloco de estudo já está ativo.");
		}

		@GetMapping("/unprocessable")
		void unprocessable() {
			throw BusinessRuleException.unprocessable("Conteúdo insuficiente.");
		}

		@GetMapping("/invalid")
		void invalid() {
			throw new InvalidInputException("A quantidade deve estar entre 1 e 10.");
		}

		@GetMapping("/gateway")
		void gateway() {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
		}

		@GetMapping("/boom")
		void boom() {
			throw new IllegalStateException("detalhe interno secreto");
		}

		@GetMapping("/upload")
		void upload() {
			throw new MaxUploadSizeExceededException(DataSize.ofMegabytes(10).toBytes());
		}

		@GetMapping("/items/{id}")
		void item(@PathVariable UUID id) {
		}

		@PostMapping("/payload")
		void payload(@Valid @RequestBody Payload payload) {
		}
	}

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
			.setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10)))
			.build();

	@Test
	void notFoundKeepsStatusAndMessage() throws Exception {
		mockMvc.perform(get("/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Não encontrado"))
				.andExpect(jsonPath("$.message").value("Bloco de estudo não encontrado."))
				.andExpect(jsonPath("$.fields").doesNotExist())
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void businessRuleIsConflictOrUnprocessable() throws Exception {
		mockMvc.perform(get("/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("O bloco de estudo já está ativo."));
		mockMvc.perform(get("/unprocessable"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.message").value("Conteúdo insuficiente."));
	}

	@Test
	void invalidInputIsBadRequest() throws Exception {
		mockMvc.perform(get("/invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("A quantidade deve estar entre 1 e 10."));
	}

	@Test
	void statusWithoutReasonGetsPortugueseDefault() throws Exception {
		mockMvc.perform(get("/gateway"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value("O serviço externo retornou uma resposta inválida."));
	}

	@Test
	void unexpectedErrorHidesInternalDetails() throws Exception {
		mockMvc.perform(get("/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("Ocorreu um erro inesperado. Tente novamente mais tarde."))
				.andExpect(content().string(not(containsString("secreto"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));
	}

	@Test
	void oversizedUploadIs413() throws Exception {
		mockMvc.perform(get("/upload"))
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.message").value(
						"O arquivo excede o tamanho máximo permitido de 10 MB. Divida o arquivo ou reduza o tamanho antes de enviar."));
	}

	@Test
	void validationErrorListsFields() throws Exception {
		mockMvc.perform(post("/payload").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Há campos com valores inválidos."))
				.andExpect(jsonPath("$.fields.name").exists());
	}

	@Test
	void malformedBodyAndBadPathVariableAreBadRequest() throws Exception {
		mockMvc.perform(post("/payload").contentType(MediaType.APPLICATION_JSON).content("{nao é json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("O corpo da requisição está ausente ou mal formatado."));
		mockMvc.perform(get("/items/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.id").value("valor inválido"));
	}
}
