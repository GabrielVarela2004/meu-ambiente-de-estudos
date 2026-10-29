package br.com.estudos.estudos_backend.career.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import br.com.estudos.estudos_backend.career.domain.CareerJob;
import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService.CompetencyItem;
import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService.UserCareerSkillsProfile;

@Service
public class CareerJobCompatibilityService {

	private static final List<String> COMMON_TECH_TERMS = List.of(
			"java", "spring boot", "spring", "postgresql", "sql", "react", "typescript",
			"javascript", "docker", "git", "rest", "restful", "api", "microsserviços", "microservices",
			"junit", "aws", "python", "node", "kubernetes", "ci/cd", "linux", "tdd", "clean architecture",
			"solid", "jpa", "hibernate", "maven", "gradle"
	);

	public record JobMatchEvaluation(
			int matchPercent,
			List<String> matchedRequirements,
			List<String> partialRequirements,
			List<String> missingRequirements,
			List<String> recommendedSkillsToDevelop,
			String summaryNote
	) {}

	public JobMatchEvaluation evaluateCompatibility(CareerJob job, UserCareerSkillsProfile profile) {
		String textToSearch = (job.getTitle() + " " + (job.getTags() != null ? job.getTags() : "") + " " + job.getDescription())
				.toLowerCase(Locale.ROOT);

		Set<String> extractedJobTechs = new HashSet<>();
		for (String term : COMMON_TECH_TERMS) {
			if (containsWord(textToSearch, term)) {
				extractedJobTechs.add(capitalize(term));
			}
		}

		if (extractedJobTechs.isEmpty()) {
			// fallback para termos do título
			Arrays.stream(job.getTitle().split("[ /,-]+"))
					.filter(w -> w.length() > 2)
					.forEach(w -> extractedJobTechs.add(capitalize(w)));
		}

		Set<String> demonstratedNames = new HashSet<>();
		for (CompetencyItem c : profile.demonstrated()) {
			demonstratedNames.add(c.skillName().toLowerCase(Locale.ROOT));
		}

		Set<String> inDevNames = new HashSet<>();
		for (CompetencyItem c : profile.inDevelopment()) {
			inDevNames.add(c.skillName().toLowerCase(Locale.ROOT));
		}

		Set<String> studiedNames = new HashSet<>();
		for (CompetencyItem c : profile.studied()) {
			studiedNames.add(c.skillName().toLowerCase(Locale.ROOT));
		}

		List<String> matched = new ArrayList<>();
		List<String> partial = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		for (String tech : extractedJobTechs) {
			String lower = tech.toLowerCase(Locale.ROOT);
			if (matchesAny(lower, demonstratedNames)) {
				matched.add(tech);
			} else if (matchesAny(lower, inDevNames) || matchesAny(lower, studiedNames)) {
				partial.add(tech);
			} else {
				missing.add(tech);
			}
		}

		int totalTechs = extractedJobTechs.size();
		int score;
		if (totalTechs == 0) {
			score = 50; // neutro
		} else {
			double rawScore = ((matched.size() * 1.0) + (partial.size() * 0.5)) / totalTechs * 100.0;
			score = (int) Math.round(rawScore);
		}
		score = Math.clamp(score, 0, 100);

		List<String> recommended = new ArrayList<>(missing);
		if (recommended.size() > 5) {
			recommended = recommended.subList(0, 5);
		}

		String note = score >= 75
				? "Alta compatibilidade com as competências demonstradas na sua plataforma de estudos."
				: score >= 45
				? "Compatibilidade moderada. Há tópicos estudados que podem ser reforçados com exercícios práticos."
				: "Oportunidade de expansão: a vaga requer tecnologias que ainda não possuem evidências nos seus cadernos.";

		return new JobMatchEvaluation(score, matched, partial, missing, recommended, note);
	}

	private boolean matchesAny(String needle, Set<String> haystack) {
		for (String h : haystack) {
			if (h.contains(needle) || needle.contains(h)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsWord(String source, String word) {
		return source.contains(word);
	}

	private String capitalize(String text) {
		if (text.equalsIgnoreCase("sql") || text.equalsIgnoreCase("api") || text.equalsIgnoreCase("aws") || text.equalsIgnoreCase("tdd")) {
			return text.toUpperCase(Locale.ROOT);
		}
		if (text.length() <= 1) return text.toUpperCase(Locale.ROOT);
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
