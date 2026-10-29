package br.com.estudos.estudos_backend.career.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerPreferencesRepository;
import br.com.estudos.estudos_backend.career.domain.CareerPreferences;
import br.com.estudos.estudos_backend.career.domain.JobModality;
import br.com.estudos.estudos_backend.security.CurrentUserResolver;

@Service
public class CareerPreferencesService {

	private final CareerPreferencesRepository repository;
	private final CurrentUserResolver currentUserResolver;

	public CareerPreferencesService(CareerPreferencesRepository repository, CurrentUserResolver currentUserResolver) {
		this.repository = repository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional(readOnly = true)
	public CareerPreferences getPreferences() {
		String ownerId = currentUserResolver.currentUserId();
		return repository.findByOwnerId(ownerId)
				.orElseGet(() -> repository.save(new CareerPreferences(ownerId)));
	}

	@Transactional
	public CareerPreferences updatePreferences(
			String targetRoles,
			String experienceLevels,
			String technologies,
			String mandatoryKeywords,
			String preferredKeywords,
			String location,
			JobModality modality,
			BigDecimal minSalary,
			String searchDays,
			int searchTimeHour,
			int searchTimeMinute,
			int maxRoundMinutes,
			boolean agentEnabled) {
		CareerPreferences prefs = getPreferences();

		if (targetRoles != null && !targetRoles.isBlank()) {
			prefs.setTargetRoles(targetRoles.trim());
		}
		if (experienceLevels != null && !experienceLevels.isBlank()) {
			prefs.setExperienceLevels(experienceLevels.trim());
		}
		if (technologies != null && !technologies.isBlank()) {
			prefs.setTechnologies(technologies.trim());
		}
		prefs.setMandatoryKeywords(mandatoryKeywords != null ? mandatoryKeywords.trim() : "");
		prefs.setPreferredKeywords(preferredKeywords != null ? preferredKeywords.trim() : "");
		prefs.setLocation(location != null && !location.isBlank() ? location.trim() : "Brasil / Remoto");
		if (modality != null) {
			prefs.setModality(modality);
		}
		prefs.setMinSalary(minSalary);
		if (searchDays != null && !searchDays.isBlank()) {
			prefs.setSearchDays(searchDays.trim());
		}
		prefs.setSearchTimeHour(Math.clamp(searchTimeHour, 0, 23));
		prefs.setSearchTimeMinute(Math.clamp(searchTimeMinute, 0, 59));
		prefs.setMaxRoundMinutes(Math.clamp(maxRoundMinutes, 5, 120));
		prefs.setAgentEnabled(agentEnabled);

		return repository.save(prefs);
	}
}
