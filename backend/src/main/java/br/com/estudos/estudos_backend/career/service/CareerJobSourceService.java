package br.com.estudos.estudos_backend.career.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.estudos_backend.career.adapter.out.persistence.CareerJobSourceRepository;
import br.com.estudos.estudos_backend.career.domain.CareerJobSource;
import br.com.estudos.estudos_backend.career.port.JobProviderPort;

@Service
public class CareerJobSourceService {

	private final CareerJobSourceRepository repository;
	private final List<JobProviderPort> providers;

	public CareerJobSourceService(CareerJobSourceRepository repository, List<JobProviderPort> providers) {
		this.repository = repository;
		this.providers = providers;
	}

	@Transactional(readOnly = true)
	public List<CareerJobSource> listSources() {
		return repository.findAll();
	}

	@Transactional
	public CareerJobSource toggleSource(UUID id, boolean enabled) {
		CareerJobSource source = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Fonte de vagas não encontrada: " + id));
		source.setEnabled(enabled);
		return repository.save(source);
	}

	@Transactional
	public CareerJobSource testSource(UUID id) {
		CareerJobSource source = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Fonte de vagas não encontrada: " + id));

		JobProviderPort provider = providers.stream()
				.filter(p -> p.supports(source))
				.findFirst()
				.orElse(null);

		source.setLastCheckedAt(OffsetDateTime.now());
		if (provider == null) {
			source.setLastStatus("ERROR");
			source.setLastError("Nenhum adaptador configurado para suportar esta fonte.");
		} else {
			boolean ok = provider.testConnection(source);
			if (ok) {
				source.setLastStatus("SUCCESS");
				source.setLastError(null);
			} else {
				source.setLastStatus("ERROR");
				source.setLastError("Falha ao comunicar com o endpoint da fonte.");
			}
		}
		return repository.save(source);
	}
}
