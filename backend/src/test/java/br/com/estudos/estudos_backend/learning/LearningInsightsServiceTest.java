package br.com.estudos.estudos_backend.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.estudos.estudos_backend.learning.LearningInsightsService.Consistency;
import br.com.estudos.estudos_backend.learning.LearningInsightsService.DifficultyStat;
import br.com.estudos.estudos_backend.learning.LearningInsightsService.BlockStat;

/** Regras de recomendação da análise de desempenho (Etapa I). */
class LearningInsightsServiceTest {

	private static final List<DifficultyStat> NEUTRAL_DIFFICULTY = List.of(
			new DifficultyStat("EASY", 2, 1, 50), new DifficultyStat("INTERMEDIATE", 2, 1, 50), new DifficultyStat("HARD", 0, 0, null));

	@Test
	void asksToStartWhenThereIsNoActivity() {
		List<String> tips = LearningInsightsService.recommendations(new Consistency(0, 0, 0, null),
				NEUTRAL_DIFFICULTY, List.of(), null, 0, 0);
		assertEquals(1, tips.size());
		assertTrue(tips.get(0).startsWith("Comece"));
	}

	@Test
	void flagsInactivityDropAndWeakBlocks() {
		List<String> tips = LearningInsightsService.recommendations(new Consistency(0, 5, 0, null),
				NEUTRAL_DIFFICULTY, List.of(new BlockStat("Herança", 6, 33), new BlockStat("Listas", 4, 90)), -15, 20, 1);

		assertTrue(tips.stream().anyMatch(t -> t.contains("não estudou nos últimos 7 dias")));
		assertTrue(tips.stream().anyMatch(t -> t.contains("caiu 15 pontos")));
		assertTrue(tips.stream().anyMatch(t -> t.contains("\"Herança\" (33%")));
		assertTrue(tips.stream().noneMatch(t -> t.contains("Listas")), "blocos fortes não viram recomendação de revisão");
		assertTrue(tips.stream().anyMatch(t -> t.contains("Menos de 20%")));
	}

	@Test
	void adjustsDifficultyAdvice() {
		List<DifficultyStat> stats = List.of(new DifficultyStat("EASY", 10, 9, 90),
				new DifficultyStat("INTERMEDIATE", 4, 2, 50), new DifficultyStat("HARD", 8, 2, 25));
		List<String> tips = LearningInsightsService.recommendations(new Consistency(5, 12, 3, null), stats, List.of(), 12, 22, 8);

		assertTrue(tips.stream().anyMatch(t -> t.contains("questões difíceis está em 25%")));
		assertTrue(tips.stream().anyMatch(t -> t.contains("domina as questões fáceis (90%)")));
		assertTrue(tips.stream().anyMatch(t -> t.contains("subiu 12 pontos")));
	}

	@Test
	void praisesSteadyProgress() {
		List<String> tips = LearningInsightsService.recommendations(new Consistency(5, 15, 5, null),
				NEUTRAL_DIFFICULTY, List.of(new BlockStat("Listas", 5, 80)), 2, 12, 5);
		assertEquals(List.of("Bom ritmo! Mantenha a constância e use a revisão espaçada para não esquecer o que já domina."), tips);
	}
}
