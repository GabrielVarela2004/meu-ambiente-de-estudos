package br.com.estudos.estudos_backend.career.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Perfil profissional do usuário (formação, experiências, projetos), usado na análise de vagas. */
@Entity
@Table(name = "career_profiles")
public class CareerProfile {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, unique = true, length = 120)
	private String ownerId;

	@Column(length = 200)
	private String headline;

	@Column(columnDefinition = "TEXT")
	private String summary;

	@Column(columnDefinition = "TEXT")
	private String education;

	@Column(columnDefinition = "TEXT")
	private String experience;

	@Column(columnDefinition = "TEXT")
	private String projects;

	@Column(columnDefinition = "TEXT")
	private String certifications;

	@Column(columnDefinition = "TEXT")
	private String languages;

	@Column(columnDefinition = "TEXT")
	private String links;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected CareerProfile() {
	}

	public CareerProfile(String ownerId) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
	}

	@PrePersist
	void onCreate() {
		if (this.id == null) {
			this.id = UUID.randomUUID();
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = OffsetDateTime.now();
	}

	public void update(String headline, String summary, String education, String experience, String projects,
			String certifications, String languages, String links) {
		this.headline = headline;
		this.summary = summary;
		this.education = education;
		this.experience = experience;
		this.projects = projects;
		this.certifications = certifications;
		this.languages = languages;
		this.links = links;
	}

	/** Verdadeiro quando nenhum campo foi preenchido. */
	public boolean isEmpty() {
		return isBlank(headline) && isBlank(summary) && isBlank(education) && isBlank(experience)
				&& isBlank(projects) && isBlank(certifications) && isBlank(languages) && isBlank(links);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public UUID getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getHeadline() {
		return headline;
	}

	public String getSummary() {
		return summary;
	}

	public String getEducation() {
		return education;
	}

	public String getExperience() {
		return experience;
	}

	public String getProjects() {
		return projects;
	}

	public String getCertifications() {
		return certifications;
	}

	public String getLanguages() {
		return languages;
	}

	public String getLinks() {
		return links;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
