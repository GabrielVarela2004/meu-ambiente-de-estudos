package br.com.estudos.estudos_backend.career.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.com.estudos.estudos_backend.career.domain.AgentExecutionStatus;
import br.com.estudos.estudos_backend.career.domain.CareerAgentExecution;

@Repository
public interface CareerAgentExecutionRepository extends JpaRepository<CareerAgentExecution, UUID> {
	List<CareerAgentExecution> findTop20ByOrderByStartedAtDesc();

	Optional<CareerAgentExecution> findFirstByStatusOrderByStartedAtDesc(AgentExecutionStatus status);

	@Query("SELECT e FROM CareerAgentExecution e WHERE e.status = 'COMPLETED' OR e.status = 'TIMED_OUT' ORDER BY e.startedAt DESC LIMIT 1")
	Optional<CareerAgentExecution> findLatestFinishedExecution();

	boolean existsByStatus(AgentExecutionStatus status);
}
