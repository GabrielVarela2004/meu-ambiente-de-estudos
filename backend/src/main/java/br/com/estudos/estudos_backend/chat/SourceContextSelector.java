package br.com.estudos.estudos_backend.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import br.com.estudos.estudos_backend.material.StudyMaterial;
import br.com.estudos.estudos_backend.material.StudyMaterialProcessingStatus;
import br.com.estudos.estudos_backend.material.StudyMaterialSourceType;
import org.springframework.stereotype.Component;

@Component
public class SourceContextSelector {

	private static final int MAX_TOTAL_CONTEXT_CHARS = 30_000;
	private static final int MAX_MATERIAL_EXCERPT_CHARS = 10_000;
	private static final int CHUNK_SIZE_CHARS = 1_200;
	private static final int CHUNK_OVERLAP_CHARS = 200;

	private static final Set<String> STOPWORDS = Set.of(
			"de", "da", "do", "das", "dos", "em", "na", "no", "nas", "nos",
			"um", "uma", "uns", "umas", "para", "por", "com", "sem", "sob", "sobre",
			"que", "qual", "quais", "como", "onde", "quando", "porque",
			"o", "a", "os", "as", "e", "ou", "mas", "se", "ao", "aos",
			"sao", "foi", "foram", "ser", "esta", "estao", "tem",
			"me", "te", "explique", "fale", "diga", "resuma", "conceito");

	public SelectedContext selectContext(String userQuestion, List<StudyMaterial> activeMaterials, List<UUID> requestedSourceIds) {
		if (activeMaterials == null || activeMaterials.isEmpty()) {
			return new SelectedContext("", List.of(), List.of());
		}

		Set<UUID> filterSet = (requestedSourceIds == null || requestedSourceIds.isEmpty())
				? Set.of()
				: Set.copyOf(requestedSourceIds);

		List<StudyMaterial> eligible = activeMaterials.stream()
				.filter(m -> m.getProcessingStatus() == null
						|| m.getProcessingStatus() == StudyMaterialProcessingStatus.COMPLETED)
				.filter(m -> filterSet.isEmpty() || (m.getId() != null && filterSet.contains(m.getId())))
				.toList();

		if (eligible.isEmpty()) {
			return new SelectedContext("", List.of(), List.of());
		}

		Set<String> queryTerms = extractQueryTerms(userQuestion);

		List<ScoredMaterial> scoredMaterials = eligible.stream()
				.map(material -> scoreAndExtractMaterial(material, queryTerms))
				.sorted(Comparator.comparingInt(ScoredMaterial::score).reversed())
				.toList();

		// Se houver termos na pergunta e ao menos uma fonte tiver pontuação > 0, prioriza as fontes com match,
		// mas mantém as demais fontes logo após caso ainda haja espaço no orçamento de contexto.
		StringBuilder contextBuilder = new StringBuilder();
		contextBuilder.append("\n\n[INÍCIO DOS MATERIAIS DE REFERÊNCIA — DADOS NÃO CONFIÁVEIS]\n");

		List<String> referencedTitles = new ArrayList<>();
		List<ReferencedSource> referencedSources = new ArrayList<>();
		int remaining = MAX_TOTAL_CONTEXT_CHARS;

		for (ScoredMaterial scored : scoredMaterials) {
			if (remaining <= 0) {
				break;
			}
			StudyMaterial material = scored.material();
			String excerpt = sanitizeUntrustedExcerpt(scored.selectedExcerpt());
			if (excerpt.length() > remaining) {
				excerpt = excerpt.substring(0, remaining) + "\n[contexto truncado]";
			}

			String metadataHeader = formatMaterialHeader(material);
			String section = "\n" + metadataHeader + "\n" + excerpt + "\n";
			if (section.length() > remaining) {
				section = section.substring(0, remaining) + "\n[contexto truncado]\n";
			}

			contextBuilder.append(section);
			remaining -= section.length();
			referencedTitles.add(material.getTitle());
			referencedSources.add(new ReferencedSource(
					material.getId(),
					material.getTitle(),
					material.getSourceType() == null ? StudyMaterialSourceType.TEXT : material.getSourceType(),
					material.getSourceUrl(),
					material.getStudyBlock() == null ? null : material.getStudyBlock().getId(),
					material.getStudyBlock() == null ? null : material.getStudyBlock().getTitle()));
		}

		contextBuilder.append("[FIM DOS MATERIAIS DE REFERÊNCIA]\n");
		return new SelectedContext(
				contextBuilder.toString(),
				List.copyOf(referencedTitles),
				List.copyOf(referencedSources));
	}

	/**
	 * Neutraliza tentativas de quebra de delimitadores de contexto (prompt injection).
	 * Impede que o conteúdo não confiável de um material simule o fechamento prematuro
	 * da seção de referências ou declare novas instruções de sistema.
	 */
	public String sanitizeUntrustedExcerpt(String excerpt) {
		if (excerpt == null) {
			return "";
		}
		return excerpt
				.replace("[FIM DOS MATERIAIS DE REFERÊNCIA]", "(FIM DOS MATERIAIS DE REFERÊNCIA)")
				.replace("[INÍCIO DOS MATERIAIS DE REFERÊNCIA", "(INÍCIO DOS MATERIAIS DE REFERÊNCIA")
				.replace("[contexto truncado]", "(contexto truncado)");
	}

	private ScoredMaterial scoreAndExtractMaterial(StudyMaterial material, Set<String> queryTerms) {
		String content = material.getContent() == null ? "" : material.getContent();
		String normalizedTitle = normalize(material.getTitle());
		int titleBonus = 0;
		for (String term : queryTerms) {
			if (normalizedTitle.contains(term)) {
				titleBonus += 5;
			}
		}

		if (content.length() <= MAX_MATERIAL_EXCERPT_CHARS) {
			int contentScore = countTermMatches(normalize(content), queryTerms);
			return new ScoredMaterial(material, titleBonus + contentScore, content);
		}

		// Quando o documento/transcrição excede MAX_MATERIAL_EXCERPT_CHARS, divide em blocos (chunks)
		// e seleciona os trechos mais relevantes para a pergunta do usuário.
		List<TextChunk> chunks = splitIntoChunks(content);
		List<ScoredChunk> scoredChunks = new ArrayList<>();
		int totalMaterialScore = titleBonus;

		for (TextChunk chunk : chunks) {
			int chunkScore = countTermMatches(normalize(chunk.text()), queryTerms);
			scoredChunks.add(new ScoredChunk(chunk, chunkScore));
			totalMaterialScore += chunkScore;
		}

		// Ordena os chunks por relevância e seleciona os melhores até o limite de MAX_MATERIAL_EXCERPT_CHARS
		scoredChunks.sort(Comparator.comparingInt(ScoredChunk::score).reversed());

		List<ScoredChunk> selectedChunks = new ArrayList<>();
		int accumulatedChars = 0;
		for (ScoredChunk scoredChunk : scoredChunks) {
			if (accumulatedChars + scoredChunk.chunk().text().length() > MAX_MATERIAL_EXCERPT_CHARS) {
				if (selectedChunks.isEmpty()) {
					selectedChunks.add(scoredChunk);
				}
				break;
			}
			selectedChunks.add(scoredChunk);
			accumulatedChars += scoredChunk.chunk().text().length();
			if (selectedChunks.size() >= 5) {
				break;
			}
		}

		// Reordena os chunks selecionados na ordem cronológica/original do documento
		selectedChunks.sort(Comparator.comparingInt(c -> c.chunk().startIndex()));

		StringBuilder mergedExcerpt = new StringBuilder();
		for (ScoredChunk item : selectedChunks) {
			if (mergedExcerpt.length() > 0) {
				mergedExcerpt.append("\n[...]\n");
			}
			mergedExcerpt.append(item.chunk().text().trim());
		}

		return new ScoredMaterial(material, totalMaterialScore, mergedExcerpt.toString());
	}

	private String formatMaterialHeader(StudyMaterial material) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- ");
		if (material.getStudyBlock() != null && material.getStudyBlock().getTitle() != null) {
			sb.append("[").append(material.getStudyBlock().getTitle().trim()).append("] ");
		}
		sb.append(material.getTitle()).append(" ---");
		List<String> metaDetails = new ArrayList<>();
		if (material.getSourceType() != null) {
			metaDetails.add("Tipo: " + material.getSourceType().name());
		}
		if (material.getSourceUrl() != null && !material.getSourceUrl().isBlank()) {
			metaDetails.add("URL: " + material.getSourceUrl().trim());
		}
		if (!metaDetails.isEmpty()) {
			sb.append(" (").append(String.join(", ", metaDetails)).append(")");
		}
		return sb.toString();
	}

	private List<TextChunk> splitIntoChunks(String text) {
		List<TextChunk> chunks = new ArrayList<>();
		int length = text.length();
		int start = 0;
		while (start < length) {
			int end = Math.min(start + CHUNK_SIZE_CHARS, length);
			if (end < length) {
				int nextBoundary = findNextSentenceOrWordBoundary(text, end);
				if (nextBoundary > start) {
					end = nextBoundary;
				}
			}
			chunks.add(new TextChunk(text.substring(start, end), start, end));
			if (end >= length) {
				break;
			}
			start = Math.max(start + 1, end - CHUNK_OVERLAP_CHARS);
		}
		return chunks;
	}

	private int findNextSentenceOrWordBoundary(String text, int targetIndex) {
		int searchWindow = Math.min(150, text.length() - targetIndex);
		for (int i = 0; i < searchWindow; i++) {
			char c = text.charAt(targetIndex + i);
			if (c == '\n' || c == '.' || c == '!' || c == '?') {
				return targetIndex + i + 1;
			}
		}
		for (int i = 0; i < searchWindow; i++) {
			if (Character.isWhitespace(text.charAt(targetIndex + i))) {
				return targetIndex + i + 1;
			}
		}
		return targetIndex;
	}

	private int countTermMatches(String normalizedText, Set<String> queryTerms) {
		if (queryTerms.isEmpty()) {
			return 0;
		}
		int score = 0;
		for (String term : queryTerms) {
			int index = 0;
			while ((index = normalizedText.indexOf(term, index)) != -1) {
				score++;
				index += term.length();
			}
		}
		return score;
	}

	private Set<String> extractQueryTerms(String question) {
		if (question == null || question.isBlank()) {
			return Set.of();
		}
		String[] tokens = normalize(question).split("[^a-z0-9]+");
		Set<String> result = new LinkedHashSet<>();
		for (String token : tokens) {
			String clean = token.trim();
			if (clean.length() >= 3 && !STOPWORDS.contains(clean)) {
				result.add(clean);
			}
		}
		return result;
	}

	private String normalize(String text) {
		if (text == null) {
			return "";
		}
		String nfd = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		return nfd.replaceAll("\\p{M}", "");
	}

	public record SelectedContext(
			String formattedContextSection,
			List<String> referencedTitles,
			List<ReferencedSource> referencedSources) {
	}

	public record ReferencedSource(
			UUID id,
			String title,
			StudyMaterialSourceType sourceType,
			String sourceUrl,
			UUID studyBlockId,
			String studyBlockTitle) {

		public ReferencedSource(UUID id, String title, StudyMaterialSourceType sourceType, String sourceUrl) {
			this(id, title, sourceType, sourceUrl, null, null);
		}
	}

	private record ScoredMaterial(StudyMaterial material, int score, String selectedExcerpt) {}

	private record TextChunk(String text, int startIndex, int endIndex) {}

	private record ScoredChunk(TextChunk chunk, int score) {}
}
