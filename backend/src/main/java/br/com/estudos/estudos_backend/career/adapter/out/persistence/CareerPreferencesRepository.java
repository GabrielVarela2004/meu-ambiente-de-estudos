package br.com.estudos.estudos_backend.career.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.estudos.estudos_backend.career.domain.CareerPreferences;

@Repository
public interface CareerPreferencesRepository extends JpaRepository<CareerPreferences, UUID> {
	Optional<CareerPreferences> findByOwnerId(String ownerId);
}
