package br.com.estudos.estudos_backend.career.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService;
import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService.UserCareerSkillsProfile;
import br.com.estudos.estudos_backend.exercise.PracticalExercise;
import br.com.estudos.estudos_backend.exercise.PracticalExerciseService;
import br.com.estudos.estudos_backend.knowledge.KnowledgeTopic;

@RestController
@RequestMapping("/api/career/skills")
public class CareerSkillsController {

	private final CareerSkillsProfileService service;
	private final PracticalExerciseService exerciseService;

	public CareerSkillsController(CareerSkillsProfileService service, PracticalExerciseService exerciseService) {
		this.service = service;
		this.exerciseService = exerciseService;
	}

	@GetMapping
	public UserCareerSkillsProfile getSkillsProfile() {
		return service.getUserSkillsProfile();
	}

	/**
	 * Gera um exercício prático (desafio de validação) para o tópico que representa a competência.
	 * O resultado das tentativas alimenta o nível da competência no perfil.
	 */
	@PostMapping("/assess")
	public ResponseEntity<AssessmentResponse> assess(@Valid @RequestBody AssessmentRequest request) {
		KnowledgeTopic topic = service.findTopicForSkill(request.skillName())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competência não encontrada"));
		UUID blockId = topic.getStudyBlock().getId();
		PracticalExercise exercise = exerciseService.generateFromMaterials(blockId, null, topic.getId(), null, null);
		return ResponseEntity.status(HttpStatus.CREATED).body(new AssessmentResponse(
				exercise.getId(), blockId, topic.getStudyBlock().getTitle(), exercise.getTitle()));
	}

	public record AssessmentRequest(@NotBlank @Size(max = 300) String skillName) {}

	public record AssessmentResponse(UUID exerciseId, UUID studyBlockId, String studyBlockTitle, String exerciseTitle) {}
}
