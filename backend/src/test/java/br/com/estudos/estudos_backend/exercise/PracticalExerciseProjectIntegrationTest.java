package br.com.estudos.estudos_backend.exercise;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.estudos.estudos_backend.chat.AiTutorProvider;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorReply;
import br.com.estudos.estudos_backend.chat.AiTutorProvider.AiTutorRequest;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.RunStatus;
import br.com.estudos.estudos_backend.exercise.CodeEvaluationReport.TestResult;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/** Projeto com vários arquivos: Executar (sem tentativa), rascunho, envio para correção e compatibilidade. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticalExerciseProjectIntegrationTest {

	private static final String JAVA_EXERCISE = """
			{"title":"Pedidos","prompt":"Crie o enum Status e a classe Pedido.","steps":["Enum","Classe"],
			 "language":"JAVA","starterCode":"public class Main { public static void main(String[] a) {} }",
			 "starterFiles":[{"path":"Main.java","content":"public class Main { public static void main(String[] a) {} }"},
			                 {"path":"Status.java","content":"public enum Status { }"},
			                 {"path":"../fora.java","content":"invalido"}],
			 "solutionCode":"SOLUCAO_REFERENCIA","testCode":"class PedidoTest {}","conceptChecks":[],
			 "hintsJson":"[]","testCasesJson":"[]","sourceExcerpt":"Enums."}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PracticalExerciseAttemptRepository attemptRepository;

	@MockitoBean
	private AiTutorProvider aiTutorProvider;

	@MockitoBean
	private CodeRunner codeRunner;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private String userId;
	private String blockId;
	private String exercisePath;
	private UUID exerciseId;

	@BeforeEach
	void setUp() throws Exception {
		userId = "projeto-" + UUID.randomUUID();
		when(aiTutorProvider.isConfigured()).thenReturn(true);
		when(aiTutorProvider.generateReply(any(AiTutorRequest.class))).thenReturn(new AiTutorReply(JAVA_EXERCISE, "Fake", "fake"));
		when(codeRunner.isAvailable()).thenReturn(true);
		when(codeRunner.run(contains("SOLUCAO_REFERENCIA"), anyString(), anyList())).thenReturn(new CodeEvaluationReport(
				RunStatus.OK, null, List.of(), List.of(new TestResult("a()", true, true, null)), List.of(), List.of(), "{}"));

		String block = mockMvc.perform(as(post("/api/study-blocks")).content("{\"title\":\"Java\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		blockId = objectMapper.readTree(block).get("id").asText();
		mockMvc.perform(as(post("/api/study-blocks/" + blockId + "/materials"))
				.content("{\"title\":\"Enums\",\"content\":\"Um enum define constantes.\"}"))
				.andExpect(status().isCreated());
		String generated = mockMvc.perform(as(post("/api/study-blocks/" + blockId + "/exercises/generate")).content("{}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		exerciseId = UUID.fromString(objectMapper.readTree(generated).get("id").asText());
		exercisePath = "/api/study-blocks/" + blockId + "/exercises/" + exerciseId;
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder) {
		return builder.header(CurrentUserResolver.USER_HEADER, userId).contentType(MediaType.APPLICATION_JSON);
	}

	@Test
	void generatedExerciseExposesStarterFilesAndIgnoresInvalidOnes() throws Exception {
		// Um caminho inválido invalida o conjunto: o projeto inicial cai para o starterCode como arquivo único.
		mockMvc.perform(as(get("/api/study-blocks/" + blockId + "/exercises")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].starterFiles.length()").value(1))
				.andExpect(jsonPath("$[0].starterFiles[0].path").value("Main.java"));
	}

	@Test
	void runExecutesTheProjectWithStdinAndNeverCountsAsAttempt() throws Exception {
		when(codeRunner.execute(anyList(), eq("Ana\n"))).thenReturn(new CodeExecutionResult(
				CodeExecutionResult.Status.OK, null, "Main", 0, 35, "Olá, Ana\n", "", List.of()));

		mockMvc.perform(as(post(exercisePath + "/run")).content("""
				{"files":[{"path":"Main.java","content":"public class Main {}"},
				          {"path":"loja/Status.java","content":"package loja; public enum Status { A }"}],
				 "stdin":"Ana\\n"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OK"))
				.andExpect(jsonPath("$.stdout").value("Olá, Ana\n"))
				.andExpect(jsonPath("$.timeMillis").value(35));

		verify(codeRunner).execute(argThat(files -> files.size() == 2 && files.get(1).path().equals("loja/Status.java")), eq("Ana\n"));
		org.assertj.core.api.Assertions.assertThat(attemptRepository.findAllByExerciseIdOrderByCreatedAtDesc(exerciseId)).isEmpty();
	}

	@Test
	void runReportsCompileErrorsWithFileLineAndColumn() throws Exception {
		when(codeRunner.execute(anyList(), any())).thenReturn(new CodeExecutionResult(CodeExecutionResult.Status.COMPILATION_ERROR,
				null, null, null, 0, "", "", List.of(new CodeExecutionResult.CompileDiagnostic("loja/Pedido.java", 4, 9, "';' expected"))));

		mockMvc.perform(as(post(exercisePath + "/run")).content("{\"files\":[{\"path\":\"loja/Pedido.java\",\"content\":\"x\"}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPILATION_ERROR"))
				.andExpect(jsonPath("$.compileDiagnostics[0].file").value("loja/Pedido.java"))
				.andExpect(jsonPath("$.compileDiagnostics[0].line").value(4))
				.andExpect(jsonPath("$.compileDiagnostics[0].column").value(9));
	}

	@Test
	void pathTraversalAndLimitsAreRejected() throws Exception {
		mockMvc.perform(as(post(exercisePath + "/run")).content("{\"files\":[{\"path\":\"../../etc/passwd.java\",\"content\":\"x\"}]}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(as(post(exercisePath + "/run")).content("{\"files\":[{\"path\":\"/tmp/A.java\",\"content\":\"x\"}]}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(as(post(exercisePath + "/run")).content("{\"files\":[{\"path\":\"Script.sh\",\"content\":\"x\"}]}"))
				.andExpect(status().isBadRequest());
		String big = "x".repeat(110 * 1024);
		mockMvc.perform(as(put(exercisePath + "/draft")).content("{\"files\":[{\"path\":\"A.java\",\"content\":\"" + big + "\"}]}"))
				.andExpect(status().isBadRequest());
		verify(codeRunner, never()).execute(anyList(), any());
	}

	@Test
	void draftIsSavedRestoredAndDeleted() throws Exception {
		mockMvc.perform(as(get(exercisePath + "/draft"))).andExpect(status().isNoContent());

		mockMvc.perform(as(put(exercisePath + "/draft")).content("""
				{"files":[{"path":"Main.java","content":"// v1"},{"path":"Cor.java","content":"enum Cor { A }"}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.files.length()").value(2));
		mockMvc.perform(as(put(exercisePath + "/draft")).content("{\"files\":[{\"path\":\"Main.java\",\"content\":\"// v2\"}]}"))
				.andExpect(status().isOk());

		mockMvc.perform(as(get(exercisePath + "/draft")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.files.length()").value(1))
				.andExpect(jsonPath("$.files[0].content").value("// v2"));

		// Outro usuário não vê o rascunho.
		mockMvc.perform(get(exercisePath + "/draft").header(CurrentUserResolver.USER_HEADER, "outro-" + userId))
				.andExpect(status().isNotFound());

		mockMvc.perform(as(delete(exercisePath + "/draft"))).andExpect(status().isNoContent());
		mockMvc.perform(as(get(exercisePath + "/draft"))).andExpect(status().isNoContent());
	}

	@Test
	void submissionWithFilesIsCorrectedAsProjectAndKeepsTheFiles() throws Exception {
		when(codeRunner.runProject(anyList(), anyString(), anyList())).thenReturn(new CodeEvaluationReport(
				RunStatus.OK, null, List.of(), List.of(new TestResult("a()", true, true, null)), List.of(), List.of(), "{\"status\":\"OK\"}"));

		mockMvc.perform(as(post(exercisePath + "/attempts")).content("""
				{"files":[{"path":"Main.java","content":"public class Main {}"},
				          {"path":"Status.java","content":"public enum Status { ABERTO }"}]}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.passed").value(true))
				.andExpect(jsonPath("$.files.length()").value(2))
				.andExpect(jsonPath("$.files[1].path").value("Status.java"))
				.andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("// Arquivo: Status.java")));

		verify(codeRunner).runProject(argThat(files -> files.size() == 2), anyString(), anyList());
	}

	@Test
	void oldSingleTextSubmissionStillWorksAndIsStoredAsOneFile() throws Exception {
		when(codeRunner.run(contains("ALUNO"), anyString(), anyList())).thenReturn(new CodeEvaluationReport(
				RunStatus.OK, null, List.of(), List.of(new TestResult("a()", true, true, null)), List.of(), List.of(), "{}"));

		mockMvc.perform(as(post(exercisePath + "/attempts")).content("{\"answer\":\"// ALUNO\\npublic class Pedido {}\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.files.length()").value(1))
				.andExpect(jsonPath("$.files[0].path").value("Pedido.java"));

		verify(codeRunner, never()).runProject(anyList(), anyString(), anyList());
		mockMvc.perform(as(post(exercisePath + "/attempts")).content("{}"))
				.andExpect(status().isBadRequest());
	}
}
