package br.com.estudos.estudos_backend.material;

import java.util.List;
import java.util.UUID;

import br.com.estudos.estudos_backend.exercise.PracticalExercise;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttempt;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseService;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseType;
import br.com.estudos.estudos_backend.question.StudyQuestion;
import br.com.estudos.estudos_backend.question.StudyQuestionController;
import br.com.estudos.estudos_backend.question.StudyQuestionDifficulty;
import br.com.estudos.estudos_backend.question.StudyQuestionFormat;
import br.com.estudos.estudos_backend.question.StudyQuestionService;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SharedMaterialLibraryIntegrationTest {

	@Autowired
	private StudyMaterialService materialService;

	@Autowired
	private StudyMaterialRepository materialRepository;

	@Autowired
	private StudyBlockRepository blockRepository;

	@Autowired
	private StudyQuestionService questionService;

	@Autowired
	private PracticalExerciseService exerciseService;

	private StudyBlock blockA;
	private StudyBlock blockB;

	@BeforeEach
	void setUp() {
		blockA = blockRepository.save(new StudyBlock(
				"default-user",
				"Estruturas de Dados Avançadas",
				"Estudo de coleções, listas e árvores",
				"Dominar estruturas"));

		blockB = blockRepository.save(new StudyBlock(
				"default-user",
				"Banco de Dados Relacional",
				"Estudo de SQL e índices",
				"Dominar PostgreSQL"));
	}

	@Test
	void singleMaterialRegisteredOnceIsReusedByTheoryAndPracticeWithoutDuplication() {
		// 1. Cadastra o material canônico UMA ÚNICA VEZ
		StudyMaterial canonicalMaterial = materialService.create(
				blockA.getId(),
				"Guia de Listas e Coleções",
				"Uma lista em Java é uma coleção ordenada de elementos que permite duplicatas.");

		assertNotNull(canonicalMaterial.getId());
		long materialCount = materialRepository.countByStudyBlockIdAndStatus(
				blockA.getId(), StudyMaterialStatus.ACTIVE);
		assertEquals(1, materialCount, "Deve existir apenas 1 registro canônico de material no banco");

		// 2. Aba Teórica consome o material cadastrado
		StudyQuestion question = questionService.create(
				blockA.getId(),
				canonicalMaterial.getId(),
				null,
				"Uma lista em Java é uma coleção ordenada...",
				"Qual das características define uma lista?",
				"Listas mantêm ordem de inserção",
				StudyQuestionDifficulty.EASY,
				StudyQuestionFormat.MULTIPLE_CHOICE,
				null,
				List.of(
						new StudyQuestionController.OptionRequest("Permite duplicatas e é ordenada", true),
						new StudyQuestionController.OptionRequest("Não permite elementos repetidos", false)));

		assertNotNull(question.getId());
		assertEquals(canonicalMaterial.getId(), question.getStudyMaterial().getId());

		// 3. Aba Prática consome o MESMO material sem fazer novo upload
		PracticalExercise exercise = exerciseService.create(
				blockA.getId(),
				canonicalMaterial.getId(),
				null,
				"Uma lista em Java é uma coleção ordenada...",
				"Implementação de Lista Customizada",
				"Implemente uma lista encadeada simples em Java.",
				"Consulte os métodos add e get descritos no guia.",
				StudyQuestionDifficulty.INTERMEDIATE,
				PracticalExerciseType.METHOD_IMPLEMENTATION,
				"public class MinhaLista { }",
				"public class MinhaLista { ... }",
				"[\"Crie a classe Node\"]",
				"[]");

		assertNotNull(exercise.getId());
		assertEquals(canonicalMaterial.getId(), exercise.getStudyMaterial().getId());
		assertEquals("Guia de Listas e Coleções", exercise.getStudyMaterial().getTitle());
		assertTrue(exercise.getSourceExcerpt().contains("coleção ordenada"));

		// 4. Verifica que nenhuma duplicata de material foi criada
		long totalMaterialsAfterBoth = materialRepository.countByStudyBlockIdAndStatus(
				blockA.getId(), StudyMaterialStatus.ACTIVE);
		assertEquals(1, totalMaterialsAfterBoth, "Nenhum material adicional deve ter sido duplicado");

		// 5. Tentativa prática registra o vínculo com o material de origem
		PracticalExerciseAttempt attempt = exerciseService.submit(
				blockA.getId(), exercise.getId(), "public class MinhaLista { Node head; }", 5, "Excelente");
		assertNotNull(attempt.getId());
		assertEquals(canonicalMaterial.getId(), attempt.getSourceMaterialId());
	}

	@Test
	void crossBlockMaterialLinkingIsRejectedToPreventDataLeakage() {
		// Material pertence exclusivamente ao Bloco A
		StudyMaterial materialA = materialService.create(
				blockA.getId(),
				"Material Exclusivo do Bloco A",
				"Conteúdo confidencial do Bloco A.");

		// Tentativa de vincular o material do Bloco A a um exercício do Bloco B deve falhar
		assertThrows(ResponseStatusException.class, () -> exerciseService.create(
				blockB.getId(),
				materialA.getId(),
				null,
				null,
				"Exercício no Bloco B com material do Bloco A",
				"Enunciado teste",
				"Dica teste",
				StudyQuestionDifficulty.INTERMEDIATE,
				PracticalExerciseType.PROBLEM_SOLVING,
				null,
				null,
				null,
				null));
	}

	@Test
	void generatePracticalExerciseFromBlockMaterialsProducesGroundedExercise() {
		StudyMaterial material = materialService.create(
				blockA.getId(),
				"Filas e Pilhas",
				"Uma fila opera no padrão FIFO (First In, First Out). Pilhas operam em LIFO.");

		// Gera exercício prático a partir do material do bloco
		PracticalExercise generated = exerciseService.generateFromMaterials(
				blockA.getId(),
				material.getId(),
				null,
				StudyQuestionDifficulty.INTERMEDIATE,
				PracticalExerciseType.PROBLEM_SOLVING);

		assertNotNull(generated.getId());
		assertNotNull(generated.getStudyMaterial());
		assertEquals(material.getId(), generated.getStudyMaterial().getId());
		assertNotNull(generated.getSourceExcerpt());
		assertTrue(generated.getSourceExcerpt().contains("FIFO"));
		assertNotNull(generated.getPrompt());
	}

	@Test
	void archivingMaterialPreservesHistoricalExercisesAndPreventsNewLinkings() {
		StudyMaterial material = materialService.create(
				blockA.getId(),
				"Material Temporário",
				"Conteúdo que será arquivado futuramente.");

		PracticalExercise exercise = exerciseService.create(
				blockA.getId(),
				material.getId(),
				null,
				null,
				"Exercício Histórico",
				"Enunciado do exercício",
				"Dica",
				StudyQuestionDifficulty.EASY,
				PracticalExerciseType.PROBLEM_SOLVING,
				null,
				null,
				null,
				null);

		// Arquivamento lógico do material
		materialService.archive(blockA.getId(), material.getId());

		// Exercício existente permanece preservado com o vínculo histórico
		List<PracticalExercise> exercises = exerciseService.list(blockA.getId(), false);
		assertEquals(1, exercises.size());
		assertEquals(material.getId(), exercises.get(0).getStudyMaterial().getId());

		// Nova vinculação com material arquivado deve ser rejeitada
		assertThrows(ResponseStatusException.class, () -> exerciseService.create(
				blockA.getId(),
				material.getId(),
				null,
				null,
				"Novo Exercício Inválido",
				"Enunciado",
				"Dica",
				StudyQuestionDifficulty.EASY,
				PracticalExerciseType.PROBLEM_SOLVING,
				null,
				null,
				null,
				null));
	}
}
