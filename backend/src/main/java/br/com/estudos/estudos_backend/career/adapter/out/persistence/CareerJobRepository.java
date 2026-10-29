package br.com.estudos.estudos_backend.career.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.estudos.estudos_backend.career.domain.CareerJob;
import br.com.estudos.estudos_backend.career.domain.JobModality;

@Repository
public interface CareerJobRepository extends JpaRepository<CareerJob, UUID> {
	Optional<CareerJob> findByUrl(String url);

	@Query("SELECT j FROM CareerJob j WHERE j.active = true " +
	       "AND (LOWER(j.title) LIKE :pattern OR LOWER(j.company) LIKE :pattern OR LOWER(j.tags) LIKE :pattern) " +
	       "AND (:modality IS NULL OR j.modality = :modality) " +
	       "ORDER BY j.publishedAt DESC NULLS LAST, j.discoveredAt DESC")
	Page<CareerJob> searchJobsWithPattern(
			@Param("pattern") String pattern,
			@Param("modality") JobModality modality,
			Pageable pageable);

	@Query("SELECT j FROM CareerJob j WHERE j.active = true " +
	       "AND (:modality IS NULL OR j.modality = :modality) " +
	       "ORDER BY j.publishedAt DESC NULLS LAST, j.discoveredAt DESC")
	Page<CareerJob> findAllByModality(
			@Param("modality") JobModality modality,
			Pageable pageable);

	List<CareerJob> findTop50ByActiveTrueOrderByPublishedAtDesc();
}
