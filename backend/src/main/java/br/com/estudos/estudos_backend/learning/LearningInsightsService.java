package br.com.estudos.estudos_backend.learning;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.ai.AiOperation;
import br.com.estudos.estudos_backend.ai.AiOperationContext;
import br.com.estudos.estudos_backend.chat.AiTutorProvider;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttempt;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseAttemptRepository;
import br.com.estudos.estudos_backend.question.QuestionEvaluationStatus;
import br.com.estudos.estudos_backend.question.StudyQuestionAttempt;
import br.com.estudos.estudos_backend.question.StudyQuestionAttemptRepository;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;
import br.com.estudos.estudos_backend.studyblock.StudyBlock;
import br.com.estudos.estudos_backend.studyblock.StudyBlockRepository;
import br.com.estudos.estudos_backend.studysession.StudySession;
import br.com.estudos.estudos_backend.studysession.StudySessionRepository;
import br.com.estudos.estudos_backend.studysession.StudySessionStatus;

/**
 * Análises de desempenho sobre dados reais (Etapa I): agregação semanal/mensal, desempenho por dificuldade,
 * tópicos fortes e fracos, constância, recomendações por regras e análise escrita (IA ou regras).
 */
@Service
public class LearningInsightsService {

	private static final Logger log = LoggerFactory.getLogger(LearningInsightsService.class);
	private static final int WEEK_BUCKETS = 8;
	private static final int MONTH_BUCKETS = 6;
	private static final int MIN_TOPIC_ATTEMPTS = 3;
	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
	private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM/yy", Locale.forLanguageTag("pt-BR"));

	private static final String ANALYSIS_INSTRUCTION = """
			Você é um mentor de estudos. Com base apenas nas estatísticas fornecidas, escreva em português do Brasil \
			uma análise curta e prática (texto simples, sem Markdown além de marcadores):
			Diagnóstico: 2 a 3 frases sobre a evolução e o ponto mais importante.
			Pontos fortes: 1 a 3 marcadores.
			Atenção: 1 a 3 marcadores com os pontos fracos.
			Plano para os próximos 7 dias: 3 a 5 marcadores concretos (tópicos, dificuldade, tipo de exercício, tempo).
			Não invente dados que não estão nas estatísticas.""";

	public enum Period { WEEK, MONTH }

	private final StudyQuestionAttemptRepository questionAttemptRepository;
	private final PracticalExerciseAttemptRepository exerciseAttemptRepository;
	private final StudySessionRepository sessionRepository;
	private final StudyBlockRepository blockRepository;
	private final CurrentUserResolver currentUserResolver;
	private final AiTutorProvider aiTutorProvider;
	private final Clock clock;

	@Autowired
	public LearningInsightsService(StudyQuestionAttemptRepository questionAttemptRepository,
			PracticalExerciseAttemptRepository exerciseAttemptRepository, StudySessionRepository sessionRepository,
			StudyBlockRepository blockRepository, CurrentUserResolver currentUserResolver, AiTutorProvider aiTutorProvider) {
		this(questionAttemptRepository, exerciseAttemptRepository, sessionRepository, blockRepository,
				currentUserResolver, aiTutorProvider, Clock.systemDefaultZone());
	}

	LearningInsightsService(StudyQuestionAttemptRepository questionAttemptRepository,
			PracticalExerciseAttemptRepository exerciseAttemptRepository, StudySessionRepository sessionRepository,
			StudyBlockRepository blockRepository, CurrentUserResolver currentUserResolver, AiTutorProvider aiTutorProvider,
			Clock clock) {
		this.questionAttemptRepository = questionAttemptRepository;
		this.exerciseAttemptRepository = exerciseAttemptRepository;
		this.sessionRepository = sessionRepository;
		this.blockRepository = blockRepository;
		this.currentUserResolver = currentUserResolver;
		this.aiTutorProvider = aiTutorProvider;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Insights insights(Period period) {
		String ownerId = currentUserResolver.currentUserId();
		ZoneId zone = clock.getZone();
		LocalDate today = LocalDate.now(clock);

		List<Activity> activities = new ArrayList<>();
		for (StudyQuestionAttempt attempt : questionAttemptRepository.findAllByOwnerIdOrderByAnsweredAtDesc(ownerId)) {
			if (attempt.getEvaluationStatus() == QuestionEvaluationStatus.UNGRADABLE || attempt.getAnsweredAt() == null) {
				continue;
			}
			activities.add(new Activity(attempt.getAnsweredAt().atZoneSameInstant(zone).toLocalDate(), false,
					attempt.isCorrect(), normalizeDifficulty(attempt.getDifficultySnapshot()), attempt.getTopicTitleSnapshot()));
		}
		for (PracticalExerciseAttempt attempt : exerciseAttemptRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)) {
			if (attempt.getCreatedAt() == null) {
				continue;
			}
			String difficulty = attempt.getExercise() != null && attempt.getExercise().getDifficulty() != null
					? attempt.getExercise().getDifficulty().name() : "INTERMEDIATE";
			boolean passed = attempt.getScorePercent() >= 70 || attempt.getSelfRating() >= 4;
			activities.add(new Activity(attempt.getCreatedAt().atZoneSameInstant(zone).toLocalDate(), true, passed,
					difficulty, null));
		}

		List<UUID> blockIds = blockRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId).stream().map(StudyBlock::getId).toList();
		Map<LocalDate, Long> minutesByDay = new LinkedHashMap<>();
		if (!blockIds.isEmpty()) {
			for (StudySession session : sessionRepository.findAllByStudyBlockIdIn(blockIds)) {
				if (session.getStatus() != StudySessionStatus.COMPLETED || session.getStartedAt() == null || session.getEndedAt() == null) {
					continue;
				}
				long minutes = Math.max(0, java.time.Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes());
				minutesByDay.merge(session.getEndedAt().atZoneSameInstant(zone).toLocalDate(), minutes, Long::sum);
			}
		}

		List<Bucket> buckets = buckets(period, today, activities, minutesByDay);
		List<DifficultyStat> byDifficulty = byDifficulty(activities);
		List<TopicStat> topicStats = topicStats(activities);
		List<TopicStat> weakest = topicStats.stream()
				.sorted(Comparator.comparingInt(TopicStat::accuracyPercent).thenComparing(TopicStat::attempts, Comparator.reverseOrder()))
				.limit(5).toList();
		List<TopicStat> strongest = topicStats.stream()
				.filter(t -> t.accuracyPercent() >= 70)
				.sorted(Comparator.comparingInt(TopicStat::accuracyPercent).reversed().thenComparing(TopicStat::attempts, Comparator.reverseOrder()))
				.limit(3).toList();

		Set<LocalDate> activeDays = new HashSet<>();
		activities.forEach(a -> activeDays.add(a.day()));
		minutesByDay.forEach((day, minutes) -> { if (minutes > 0) activeDays.add(day); });
		Consistency consistency = new Consistency(
				countActiveDays(activeDays, today, 7),
				countActiveDays(activeDays, today, 30),
				currentStreak(activeDays, today),
				activeDays.stream().max(LocalDate::compareTo).orElse(null));

		Bucket current = buckets.get(buckets.size() - 1);
		Bucket previous = buckets.size() > 1 ? buckets.get(buckets.size() - 2) : null;
		Integer trend = current.accuracyPercent() != null && previous != null && previous.accuracyPercent() != null
				? current.accuracyPercent() - previous.accuracyPercent() : null;

		long totalAttempts = activities.size();
		long practicalAttempts = activities.stream().filter(Activity::practical).count();
		List<String> recommendations = recommendations(consistency, byDifficulty, weakest, trend, totalAttempts, practicalAttempts);

		return new Insights(period, buckets, byDifficulty, weakest, strongest, consistency, trend,
				totalAttempts, practicalAttempts, recommendations);
	}

	/** Análise escrita: pela IA quando disponível; senão, montada a partir das próprias regras. */
	public Analysis analysis(Period period) {
		Insights data = insights(period);
		if (data.totalAttempts() == 0) {
			return new Analysis("Ainda não há tentativas registradas. Resolva questões ou exercícios práticos para "
					+ "receber uma análise do seu desempenho.", "Regras (sem dados)", OffsetDateTime.now(clock));
		}
		if (aiTutorProvider != null
				&& AiOperationContext.call(AiOperation.PERFORMANCE_ANALYSIS, aiTutorProvider::isConfigured)) {
			try {
				String stats = describe(data);
				AiTutorProvider.AiTutorReply reply = AiOperationContext.call(AiOperation.PERFORMANCE_ANALYSIS,
						() -> aiTutorProvider.generateReply(ANALYSIS_INSTRUCTION, stats));
				if (reply != null && reply.answer() != null && !reply.answer().isBlank()) {
					return new Analysis(reply.answer().strip(), "IA: " + reply.providerName() + " · " + reply.modelName(),
							OffsetDateTime.now(clock));
				}
			} catch (ResponseStatusException e) {
				log.warn("Análise por IA indisponível ({}); usando análise por regras.", e.getReason());
			}
		}
		return new Analysis(ruleBasedAnalysis(data), "Regras (sem IA)", OffsetDateTime.now(clock));
	}

	// ---------------------------------------------------------------- agregações

	private List<Bucket> buckets(Period period, LocalDate today, List<Activity> activities, Map<LocalDate, Long> minutesByDay) {
		List<Bucket> result = new ArrayList<>();
		int count = period == Period.WEEK ? WEEK_BUCKETS : MONTH_BUCKETS;
		LocalDate currentStart = period == Period.WEEK
				? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
				: today.withDayOfMonth(1);
		for (int i = count - 1; i >= 0; i--) {
			LocalDate start = period == Period.WEEK ? currentStart.minusWeeks(i) : currentStart.minusMonths(i);
			LocalDate end = period == Period.WEEK ? start.plusWeeks(1) : start.plusMonths(1);
			List<Activity> inRange = activities.stream()
					.filter(a -> !a.day().isBefore(start) && a.day().isBefore(end)).toList();
			long questionAttempts = inRange.stream().filter(a -> !a.practical()).count();
			long questionCorrect = inRange.stream().filter(a -> !a.practical() && a.success()).count();
			long practical = inRange.stream().filter(Activity::practical).count();
			long practicalPassed = inRange.stream().filter(a -> a.practical() && a.success()).count();
			long minutes = minutesByDay.entrySet().stream()
					.filter(e -> !e.getKey().isBefore(start) && e.getKey().isBefore(end))
					.mapToLong(Map.Entry::getValue).sum();
			Set<LocalDate> days = inRange.stream().map(Activity::day).collect(Collectors.toSet());
			String label = period == Period.WEEK ? "Sem. " + start.format(DAY_MONTH) : start.format(MONTH_YEAR);
			result.add(new Bucket(label, start, end.minusDays(1), questionAttempts, questionCorrect, practical,
					practicalPassed, percent(questionCorrect + practicalPassed, questionAttempts + practical), minutes, days.size()));
		}
		return result;
	}

	private static List<DifficultyStat> byDifficulty(List<Activity> activities) {
		List<DifficultyStat> result = new ArrayList<>();
		for (String difficulty : List.of("EASY", "INTERMEDIATE", "HARD")) {
			List<Activity> list = activities.stream().filter(a -> difficulty.equals(a.difficulty())).toList();
			long successes = list.stream().filter(Activity::success).count();
			result.add(new DifficultyStat(difficulty, list.size(), successes, percent(successes, list.size())));
		}
		return result;
	}

	private static List<TopicStat> topicStats(List<Activity> activities) {
		Map<String, List<Activity>> byTopic = activities.stream()
				.filter(a -> a.topic() != null && !a.topic().isBlank())
				.collect(Collectors.groupingBy(Activity::topic));
		return byTopic.entrySet().stream()
				.filter(e -> e.getValue().size() >= MIN_TOPIC_ATTEMPTS)
				.map(e -> {
					long successes = e.getValue().stream().filter(Activity::success).count();
					Integer accuracy = percent(successes, e.getValue().size());
					return new TopicStat(e.getKey(), e.getValue().size(), accuracy == null ? 0 : accuracy);
				})
				.toList();
	}

	private static int countActiveDays(Set<LocalDate> activeDays, LocalDate today, int window) {
		LocalDate from = today.minusDays(window - 1L);
		return (int) activeDays.stream().filter(d -> !d.isBefore(from) && !d.isAfter(today)).count();
	}

	/** Dias seguidos com atividade, terminando hoje (ou ontem, se ainda não estudou hoje). */
	private static int currentStreak(Set<LocalDate> activeDays, LocalDate today) {
		LocalDate day = activeDays.contains(today) ? today : today.minusDays(1);
		int streak = 0;
		while (activeDays.contains(day)) {
			streak++;
			day = day.minusDays(1);
		}
		return streak;
	}

	// ---------------------------------------------------------------- recomendações

	static List<String> recommendations(Consistency consistency, List<DifficultyStat> byDifficulty, List<TopicStat> weakest,
			Integer trend, long totalAttempts, long practicalAttempts) {
		List<String> tips = new ArrayList<>();
		if (totalAttempts == 0) {
			tips.add("Comece resolvendo algumas questões ou um exercício prático: as análises aparecem a partir das primeiras tentativas.");
			return tips;
		}
		if (consistency.activeDaysLast7() == 0) {
			tips.add("Você não estudou nos últimos 7 dias. Retome com sessões curtas (25 min) em Metas e Sessões.");
		} else if (consistency.activeDaysLast7() < 3) {
			tips.add("Estudou em " + consistency.activeDaysLast7() + " dia(s) na última semana. Distribuir o estudo em 4 ou mais dias melhora a retenção.");
		}
		if (trend != null && trend <= -10) {
			tips.add("Sua taxa de acerto caiu " + Math.abs(trend) + " pontos em relação ao período anterior. Revise as questões erradas antes de avançar.");
		} else if (trend != null && trend >= 10) {
			tips.add("Sua taxa de acerto subiu " + trend + " pontos. Aproveite para aumentar a dificuldade das questões.");
		}
		byDifficulty.stream().filter(d -> d.difficulty().equals("HARD") && d.attempts() >= 5 && d.accuracyPercent() != null && d.accuracyPercent() < 50)
				.findFirst().ifPresent(d -> tips.add("Acerto em questões difíceis está em " + d.accuracyPercent()
						+ "%. Consolide os fundamentos com questões intermediárias antes de voltar às difíceis."));
		byDifficulty.stream().filter(d -> d.difficulty().equals("EASY") && d.attempts() >= 5 && d.accuracyPercent() != null && d.accuracyPercent() >= 85)
				.findFirst().ifPresent(d -> tips.add("Você domina as questões fáceis (" + d.accuracyPercent()
						+ "%). Gere simulados com dificuldade intermediária ou difícil."));
		weakest.stream().filter(t -> t.accuracyPercent() < 60).limit(2)
				.forEach(t -> tips.add("Revise o tópico \"" + t.topic() + "\" (" + t.accuracyPercent() + "% de acerto em "
						+ t.attempts() + " tentativas): releia a fonte e gere novas questões sobre ele."));
		if (totalAttempts >= 10 && practicalAttempts * 5 < totalAttempts) {
			tips.add("Menos de 20% da sua prática é aplicada. Inclua exercícios práticos para fixar o conteúdo.");
		}
		if (tips.isEmpty()) {
			tips.add("Bom ritmo! Mantenha a constância e use a revisão espaçada para não esquecer o que já domina.");
		}
		return tips;
	}

	private static String ruleBasedAnalysis(Insights data) {
		StringBuilder out = new StringBuilder();
		Bucket current = data.buckets().get(data.buckets().size() - 1);
		String periodName = data.period() == Period.WEEK ? "nesta semana" : "neste mês";
		out.append("Diagnóstico: ");
		if (current.accuracyPercent() == null) {
			out.append("sem tentativas ").append(periodName).append(". ");
		} else {
			out.append("taxa de acerto de ").append(current.accuracyPercent()).append("% ").append(periodName)
					.append(" em ").append(current.questionAttempts() + current.practicalAttempts()).append(" tentativa(s)");
			if (data.trend() != null) {
				out.append(data.trend() >= 0 ? " (+" : " (").append(data.trend()).append(" pontos vs. período anterior)");
			}
			out.append(". ");
		}
		out.append("Dias de estudo nos últimos 7 dias: ").append(data.consistency().activeDaysLast7())
				.append("; sequência atual: ").append(data.consistency().currentStreakDays()).append(" dia(s).\n");
		if (!data.strongestTopics().isEmpty()) {
			out.append("Pontos fortes:\n");
			data.strongestTopics().forEach(t -> out.append("• ").append(t.topic()).append(" (").append(t.accuracyPercent()).append("%)\n"));
		}
		List<TopicStat> weak = data.weakestTopics().stream().filter(t -> t.accuracyPercent() < 70).toList();
		if (!weak.isEmpty()) {
			out.append("Atenção:\n");
			weak.forEach(t -> out.append("• ").append(t.topic()).append(" (").append(t.accuracyPercent()).append("%)\n"));
		}
		out.append("Plano para os próximos 7 dias:\n");
		data.recommendations().forEach(r -> out.append("• ").append(r).append('\n'));
		return out.toString().strip();
	}

	/** Estatísticas em texto compacto para a IA (sem dados pessoais além do conteúdo estudado). */
	private static String describe(Insights data) {
		StringBuilder sb = new StringBuilder("Período de agregação: ").append(data.period() == Period.WEEK ? "semanal" : "mensal").append('\n');
		sb.append("Evolução (período: tentativas, acerto %, minutos de sessão, dias ativos):\n");
		for (Bucket b : data.buckets()) {
			sb.append("- ").append(b.label()).append(": ").append(b.questionAttempts() + b.practicalAttempts()).append(" tentativas, ")
					.append(b.accuracyPercent() == null ? "sem dados" : b.accuracyPercent() + "%").append(", ")
					.append(b.studyMinutes()).append(" min, ").append(b.activeDays()).append(" dias\n");
		}
		sb.append("Por dificuldade:\n");
		data.byDifficulty().forEach(d -> sb.append("- ").append(d.difficulty()).append(": ").append(d.attempts()).append(" tentativas, ")
				.append(d.accuracyPercent() == null ? "sem dados" : d.accuracyPercent() + "%").append('\n'));
		sb.append("Tópicos mais fracos: ").append(data.weakestTopics().stream()
				.map(t -> t.topic() + " " + t.accuracyPercent() + "% (" + t.attempts() + ")").collect(Collectors.joining("; "))).append('\n');
		sb.append("Tópicos mais fortes: ").append(data.strongestTopics().stream()
				.map(t -> t.topic() + " " + t.accuracyPercent() + "%").collect(Collectors.joining("; "))).append('\n');
		sb.append("Constância: ").append(data.consistency().activeDaysLast7()).append(" dias ativos em 7, ")
				.append(data.consistency().activeDaysLast30()).append(" em 30, sequência de ")
				.append(data.consistency().currentStreakDays()).append(" dias\n");
		sb.append("Tentativas práticas: ").append(data.practicalAttempts()).append(" de ").append(data.totalAttempts()).append('\n');
		return sb.toString();
	}

	private static String normalizeDifficulty(String snapshot) {
		if (snapshot == null) {
			return "INTERMEDIATE";
		}
		String upper = snapshot.trim().toUpperCase(Locale.ROOT);
		return switch (upper) {
			case "EASY", "FACIL", "FÁCIL" -> "EASY";
			case "HARD", "DIFICIL", "DIFÍCIL" -> "HARD";
			default -> "INTERMEDIATE";
		};
	}

	private static Integer percent(long part, long total) {
		return total == 0 ? null : (int) Math.round(part * 100.0 / total);
	}

	// ---------------------------------------------------------------- tipos

	private record Activity(LocalDate day, boolean practical, boolean success, String difficulty, String topic) {}

	public record Bucket(String label, LocalDate startDate, LocalDate endDate, long questionAttempts, long questionCorrect,
			long practicalAttempts, long practicalPassed, Integer accuracyPercent, long studyMinutes, int activeDays) {}

	public record DifficultyStat(String difficulty, long attempts, long successes, Integer accuracyPercent) {}

	public record TopicStat(String topic, int attempts, int accuracyPercent) {}

	public record Consistency(int activeDaysLast7, int activeDaysLast30, int currentStreakDays, LocalDate lastActiveDay) {}

	public record Insights(Period period, List<Bucket> buckets, List<DifficultyStat> byDifficulty,
			List<TopicStat> weakestTopics, List<TopicStat> strongestTopics, Consistency consistency, Integer trend,
			long totalAttempts, long practicalAttempts, List<String> recommendations) {}

	public record Analysis(String text, String source, OffsetDateTime generatedAt) {}
}
