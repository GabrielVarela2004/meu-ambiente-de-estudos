package br.com.estudos.estudos_backend.question;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Nível de rigor da correção das questões abertas, por usuário. */
@Entity
@Table(name = "evaluation_preferences")
public class EvaluationPreference {

	@Id
	@Column(name = "owner_id", length = 120)
	private String ownerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "rigor_level", nullable = false, length = 20)
	private EvaluationRigor rigorLevel;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected EvaluationPreference() {
	}

	public EvaluationPreference(String ownerId, EvaluationRigor rigorLevel) {
		this.ownerId = ownerId;
		this.rigorLevel = rigorLevel;
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = OffsetDateTime.now();
	}

	public void changeRigor(EvaluationRigor rigorLevel) {
		this.rigorLevel = rigorLevel;
	}

	public String getOwnerId() { return ownerId; }
	public EvaluationRigor getRigorLevel() { return rigorLevel; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
