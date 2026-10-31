package br.com.estudos.estudos_backend.learning;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.estudos_backend.question.StudyQuestionDifficulty;

@RestController
public class LearningHistoryController {

	private final LearningHistoryService learningHistoryService;

	public LearningHistoryController(LearningHistoryService learningHistoryService) {
		this.learningHistoryService = learningHistoryService;
	}

	@GetMapping("/api/study-blocks/{studyBlockId}/learning-history")
	public List<LearningAttemptRecord> listBlockHistory(@PathVariable UUID studyBlockId) {
		return learningHistoryService.listBlockHistory(studyBlockId);
	}

	@GetMapping("/api/study-blocks/{studyBlockId}/indicators")
	public LearningHistoryService.ConsolidatedIndicators getBlockIndicators(@PathVariable UUID studyBlockId) {
		return learningHistoryService.calculateBlockIndicators(studyBlockId);
	}

	@GetMapping("/api/learning-history")
	public List<LearningAttemptRecord> listUserHistory() {
		return learningHistoryService.listUserHistory();
	}

	@GetMapping("/api/indicators/global")
	public LearningHistoryService.ConsolidatedIndicators getGlobalIndicators() {
		return learningHistoryService.calculateGlobalIndicators();
	}

	@GetMapping("/api/performance/global")
	public GlobalPerformanceDashboardResponse getGlobalPerformance(
			@RequestParam(required = false) Integer days,
			@RequestParam(required = false) UUID studyBlockId,
			@RequestParam(required = false) StudyQuestionDifficulty difficulty) {
		return learningHistoryService.getGlobalPerformance(days, studyBlockId, difficulty);
	}
}
