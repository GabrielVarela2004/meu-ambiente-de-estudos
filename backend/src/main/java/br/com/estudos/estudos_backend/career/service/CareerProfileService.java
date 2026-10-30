package br.com.estudos.estudos_backend.career.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerProfileRepository;
import br.com.estudos.estudos_backend.career.domain.CareerProfile;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;

/** Perfil profissional do usuário atual; criado vazio no primeiro acesso. */
@Service
public class CareerProfileService {

	private final CareerProfileRepository repository;
	private final CurrentUserResolver currentUserResolver;

	public CareerProfileService(CareerProfileRepository repository, CurrentUserResolver currentUserResolver) {
		this.repository = repository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional
	public CareerProfile getProfile() {
		String ownerId = currentUserResolver.currentUserId();
		return repository.findByOwnerId(ownerId)
				.orElseGet(() -> repository.save(new CareerProfile(ownerId)));
	}

	@Transactional
	public CareerProfile updateProfile(String headline, String summary, String education, String experience,
			String projects, String certifications, String languages, String links) {
		CareerProfile profile = getProfile();
		profile.update(clean(headline), clean(summary), clean(education), clean(experience), clean(projects),
				clean(certifications), clean(languages), clean(links));
		return repository.save(profile);
	}

	private static String clean(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}
}
