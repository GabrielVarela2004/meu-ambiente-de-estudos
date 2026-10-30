package br.com.estudos.estudos_backend.career.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.estudos.estudos_backend.career.domain.CareerProfile;

@Repository
public interface CareerProfileRepository extends JpaRepository<CareerProfile, UUID> {
	Optional<CareerProfile> findByOwnerId(String ownerId);
}
