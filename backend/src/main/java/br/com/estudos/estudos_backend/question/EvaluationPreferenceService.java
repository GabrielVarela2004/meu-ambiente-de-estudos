package br.com.estudos.estudos_backend.question;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/** Rigor da correção do usuário atual; sem preferência salva, vale RIGOROSO. */
@Service
public class EvaluationPreferenceService {

	public static final EvaluationRigor DEFAULT_RIGOR = EvaluationRigor.RIGOROSO;

	private final EvaluationPreferenceRepository repository;
	private final CurrentUserResolver currentUserResolver;

	public EvaluationPreferenceService(EvaluationPreferenceRepository repository, CurrentUserResolver currentUserResolver) {
		this.repository = repository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional(readOnly = true)
	public EvaluationRigor currentRigor() {
		return repository.findById(currentUserResolver.currentUserId())
				.map(EvaluationPreference::getRigorLevel)
				.orElse(DEFAULT_RIGOR);
	}

	@Transactional
	public EvaluationRigor updateRigor(EvaluationRigor rigor) {
		String ownerId = currentUserResolver.currentUserId();
		EvaluationPreference preference = repository.findById(ownerId)
				.orElseGet(() -> new EvaluationPreference(ownerId, rigor));
		preference.changeRigor(rigor);
		repository.save(preference);
		return rigor;
	}
}
