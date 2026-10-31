package br.com.estudos.estudos_backend.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.common.exception.BusinessRuleException;
import br.com.estudos.estudos_backend.common.exception.NotFoundException;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/**
 * Monitoramento contínuo da correção das questões abertas: distribuição das classificações, notas médias e
 * verificação de consistência — reavalia tentativas recentes com o provedor atual SEM gravar nada, para medir se a
 * correção está estável (útil ao trocar de modelo ou provedor). As tentativas são imutáveis: nada é alterado aqui.
 */
@Service
public class EvaluationMonitoringService {

	/** Amostra máxima da verificação de consistência (cada item é uma chamada à IA). */
	static final int MAX_SAMPLE = 20;

	private final StudyQuestionAttemptRepository attemptRepository;
	private final StudyQuestionService questionService;
	private final DiscursiveAnswerEvaluator evaluator;
	private final EvaluationPreferenceService preferenceService;
	private final CurrentUserResolver currentUserResolver;

	public EvaluationMonitoringService(StudyQuestionAttemptRepository attemptRepository,
			StudyQuestionService questionService, DiscursiveAnswerEvaluator evaluator,
			EvaluationPreferenceService preferenceService, CurrentUserResolver currentUserResolver) {
		this.attemptRepository = attemptRepository;
		this.questionService = questionService;
		this.evaluator = evaluator;
		this.preferenceService = preferenceService;
		this.currentUserResolver = currentUserResolver;
	}

	public record BlockStat(String blockTitle, int attempts, int correct, double averageScore) {}

	/** Estatísticas das tentativas discursivas corrigidas do usuário (modelo atual e antigo somados). */
	public record MonitoringStats(
			int graded,
			int pending,
			int correct,
			int partial,
			int incorrect,
			double averageScore,
			Map<String, Double> averageCriteria,
			List<BlockStat> blocks) {}

	/** Uma tentativa reavaliada na comparação com a classificação original. */
	public record ConsistencyItem(
			String prompt,
			String storedClassification,
			int storedScore,
			String newClassification,
			int newScore,
			boolean sameClassification) {}

	public record ConsistencyReport(int checked, int agreements, double agreementPercent,
			List<ConsistencyItem> items) {}

	@Transactional(readOnly = true)
	public MonitoringStats stats() {
		List<StudyQuestionAttempt> attempts = attemptRepository
				.findAllByOwnerIdOrderByAnsweredAtDesc(currentUserResolver.currentUserId());
		int graded = 0;
		int pending = 0;
		int correct = 0;
		int partial = 0;
		int incorrect = 0;
		long scoreSum = 0;
		Map<String, Long> criteriaSum = new LinkedHashMap<>();
		Map<String, Integer> criteriaCount = new LinkedHashMap<>();
		Map<String, int[]> byBlock = new LinkedHashMap<>();
		for (StudyQuestionAttempt attempt : attempts) {
			if (attempt.getEvaluationStatus() == QuestionEvaluationStatus.PENDING_EVALUATION
					&& attempt.getRigorLevel() != null) {
				pending++;
				continue;
			}
			if (attempt.getRigorLevel() == null || attempt.getEvaluationStatus() == null) {
				continue; // tentativa objetiva: não é correção de IA
			}
			graded++;
			switch (attempt.getEvaluationStatus()) {
				case CORRECT -> correct++;
				case PARTIAL -> partial++;
				default -> incorrect++;
			}
			scoreSum += attempt.getScorePercent();
			EvaluationJson.readCriteria(attempt.getCriteriaScoresJson())
					.forEach((criterion, score) -> {
						criteriaSum.merge(criterion, (long) score, Long::sum);
						criteriaCount.merge(criterion, 1, Integer::sum);
					});
			String blockTitle = attempt.getStudyBlockTitleSnapshot() == null
					|| attempt.getStudyBlockTitleSnapshot().isBlank() ? "Bloco sem título"
							: attempt.getStudyBlockTitleSnapshot();
			int[] counters = byBlock.computeIfAbsent(blockTitle, key -> new int[3]);
			counters[0]++;
			if (attempt.getEvaluationStatus() == QuestionEvaluationStatus.CORRECT) {
				counters[1]++;
			}
			counters[2] += attempt.getScorePercent();
		}
		Map<String, Double> averageCriteria = new LinkedHashMap<>();
		criteriaSum.forEach((criterion, sum) -> averageCriteria.put(criterion,
				Math.round(sum * 10.0 / criteriaCount.get(criterion)) / 10.0));
		List<BlockStat> blocks = byBlock.entrySet().stream()
				.map(entry -> new BlockStat(entry.getKey(), entry.getValue()[0], entry.getValue()[1],
						Math.round(entry.getValue()[2] * 10.0 / entry.getValue()[0]) / 10.0))
				.sorted((a, b) -> Integer.compare(b.attempts(), a.attempts()))
				.toList();
		return new MonitoringStats(graded, pending, correct, partial, incorrect,
				graded == 0 ? 0 : Math.round(scoreSum * 10.0 / graded) / 10.0, averageCriteria, blocks);
	}

	/**
	 * Reavalia as tentativas discursivas corrigidas mais recentes com o rigor e o provedor atuais e compara com a
	 * classificação gravada. Nada é persistido: é uma verificação, não uma recorreção. Sem IA disponível, falha com
	 * mensagem clara (chamadas de IA ficam fora de transação).
	 */
	public ConsistencyReport consistencyCheck(int limit) {
		int sample = Math.max(1, Math.min(MAX_SAMPLE, limit));
		EvaluationRigor rigor = preferenceService.currentRigor();
		List<StudyQuestionAttempt> graded = attemptRepository
				.findAllByOwnerIdOrderByAnsweredAtDesc(currentUserResolver.currentUserId()).stream()
				.filter(attempt -> attempt.getRigorLevel() != null && attempt.getEvaluationStatus() != null
						&& attempt.getEvaluationStatus() != QuestionEvaluationStatus.PENDING_EVALUATION)
				.toList();
		List<ConsistencyItem> items = new ArrayList<>();
		for (StudyQuestionAttempt attempt : graded) {
			if (items.size() >= sample) {
				break;
			}
			String answer = attempt.getUserAnswerText();
			if (answer == null || answer.isBlank()) {
				continue;
			}
			StudyQuestionService.AnswerContext context;
			try {
				context = questionService.answerContext(attempt.getStudyBlockId(), attempt.getQuestionId());
			} catch (NotFoundException | BusinessRuleException e) {
				continue; // questão apagada ou arquivada: fica fora da amostra
			}
			DiscursiveEvaluationRules.Result result;
			try {
				result = evaluator.evaluate(new DiscursiveAnswerEvaluator.Input(context.prompt(),
						context.modelAnswer(), context.rubric(), context.sourceExcerpt(), answer), rigor);
			} catch (DiscursiveAnswerEvaluator.EvaluationUnavailableException e) {
				throw new BusinessRuleException(
						"Não foi possível verificar a consistência agora: " + e.getMessage());
			}
			boolean same = attempt.getEvaluationStatus() == result.classification();
			items.add(new ConsistencyItem(truncate(context.prompt()), attempt.getEvaluationStatus().name(),
					attempt.getScorePercent(), result.classification().name(), result.overallScore(), same));
		}
		int agreements = (int) items.stream().filter(ConsistencyItem::sameClassification).count();
		double agreementPercent = items.isEmpty() ? 0
				: Math.round(agreements * 1000.0 / items.size()) / 10.0;
		return new ConsistencyReport(items.size(), agreements, agreementPercent, items);
	}

	private static String truncate(String text) {
		String value = text == null ? "" : text.strip();
		return value.length() <= 120 ? value : value.substring(0, 117) + "…";
	}
}
