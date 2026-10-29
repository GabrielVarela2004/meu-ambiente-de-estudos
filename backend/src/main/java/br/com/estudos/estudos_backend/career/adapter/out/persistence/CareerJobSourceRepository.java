package br.com.estudos.estudos_backend.career.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.estudos.estudos_backend.career.domain.CareerJobSource;

@Repository
public interface CareerJobSourceRepository extends JpaRepository<CareerJobSource, UUID> {
	Optional<CareerJobSource> findByName(String name);
	List<CareerJobSource> findByEnabledTrue();
}
