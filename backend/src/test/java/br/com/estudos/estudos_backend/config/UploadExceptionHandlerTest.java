package br.com.estudos.estudos_backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * O estouro do multipart acontece antes do controller, e o MockMvc não aplica o limite do container.
 * Por isso o teste simula a exceção num controller fictício e confere a resposta 413 em português.
 */
class UploadExceptionHandlerTest {

	@RestController
	static class OversizedUploadController {
		@PostMapping("/upload")
		void upload() {
			throw new MaxUploadSizeExceededException(DataSize.ofMegabytes(10).toBytes());
		}
	}

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OversizedUploadController())
			.setControllerAdvice(new UploadExceptionHandler(DataSize.ofMegabytes(10)))
			.build();

	@Test
	void oversizedUploadReturns413WithPortugueseMessage() throws Exception {
		mockMvc.perform(post("/upload"))
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.status").value(413))
				.andExpect(jsonPath("$.message").value(
						"O arquivo excede o tamanho máximo permitido de 10 MB. Divida o arquivo ou reduza o tamanho antes de enviar."))
				.andExpect(jsonPath("$.timestamp").exists());
	}
}
