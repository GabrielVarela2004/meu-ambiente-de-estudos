package br.com.estudos.estudos_backend.career.port;

import java.util.List;

import br.com.estudos.estudos_backend.career.domain.CareerJobSource;
import br.com.estudos.estudos_backend.career.domain.CareerPreferences;

public interface JobProviderPort {
	boolean supports(CareerJobSource source);
	List<RawJobDto> fetchJobs(CareerJobSource source, CareerPreferences preferences, int limit) throws Exception;
	boolean testConnection(CareerJobSource source);
}
