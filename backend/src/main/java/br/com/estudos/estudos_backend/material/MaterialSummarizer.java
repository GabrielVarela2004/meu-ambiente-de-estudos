package br.com.estudos.estudos_backend.material;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import br.com.estudos.estudos_backend.ai.AiOperation;
import br.com.estudos.estudos_backend.ai.AiOperationContext;
import br.com.estudos.estudos_backend.chat.AiTutorProvider;

/**
 * Gera o resumo de um material: pela IA quando há provedor disponível; caso contrário (ou se a IA falhar),
 * por um resumo extrativo que seleciona as frases mais representativas do próprio texto.
 */
@Component
public class MaterialSummarizer {

	private static final Logger log = LoggerFactory.getLogger(MaterialSummarizer.class);

	/** Trecho máximo enviado à IA (cabe nos limites de entrada de todos os provedores). */
	static final int MAX_AI_INPUT_CHARS = 30_000;
	private static final int EXTRACTIVE_SENTENCES = 6;
	private static final int KEY_TERMS = 8;

	private static final String SYSTEM_INSTRUCTION = """
			Você é um assistente de estudos. Resuma o material fornecido em português do Brasil, usando apenas o \
			que está no texto, sem inventar informações. Formato (texto simples, sem Markdown além de marcadores):
			Resumo: um parágrafo de 3 a 5 frases.
			Pontos-chave:
			• 4 a 7 marcadores curtos com as ideias principais.
			Termos importantes: lista separada por vírgulas.""";

	private static final Set<String> STOPWORDS = Set.copyOf(List.of(
			"a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "no", "na", "nos",
			"nas", "por", "pelo", "pela", "para", "pra", "com", "sem", "sob", "sobre", "e", "ou", "mas", "que", "se",
			"como", "mais", "menos", "muito", "ja", "nao", "sim", "ao", "aos", "a", "isso", "isto", "esse", "essa",
			"este", "esta", "eles", "elas", "ele", "ela", "seu", "sua", "seus", "suas", "entre", "quando", "onde",
			"tambem", "ser", "ter", "sao", "foi", "era", "pode", "podem", "deve", "the", "and", "of", "to", "in",
			"is", "for", "on", "with", "as", "by", "it", "this", "that", "are", "be", "or", "an", "from", "at"));

	private final AiTutorProvider aiTutorProvider;

	@Autowired
	public MaterialSummarizer(AiTutorProvider aiTutorProvider) {
		this.aiTutorProvider = aiTutorProvider;
	}

	public Summary summarize(String title, String content) {
		String text = content == null ? "" : content.strip();
		if (text.isBlank()) {
			return new Summary("Material sem texto para resumir.", "Extrativo (sem IA)");
		}
		if (aiTutorProvider != null
				&& AiOperationContext.call(AiOperation.MATERIAL_SUMMARY, aiTutorProvider::isConfigured)) {
			try {
				return summarizeWithAi(title, text);
			} catch (ResponseStatusException e) {
				log.warn("Resumo por IA indisponível ({}); usando resumo extrativo.", e.getReason());
			}
		}
		return extractive(text);
	}

	private Summary summarizeWithAi(String title, String text) {
		boolean partial = text.length() > MAX_AI_INPUT_CHARS;
		String excerpt = partial ? text.substring(0, MAX_AI_INPUT_CHARS) : text;
		String prompt = "Título do material: " + title + "\n"
				+ (partial ? "Atenção: o material é longo; abaixo está apenas a parte inicial ("
						+ MAX_AI_INPUT_CHARS + " de " + text.length() + " caracteres).\n" : "")
				+ "[INÍCIO DO MATERIAL]\n" + excerpt + "\n[FIM DO MATERIAL]";
		AiTutorProvider.AiTutorReply reply = AiOperationContext.call(AiOperation.MATERIAL_SUMMARY,
				() -> aiTutorProvider.generateReply(SYSTEM_INSTRUCTION, prompt));
		String answer = reply == null || reply.answer() == null ? "" : reply.answer().strip();
		if (answer.isBlank()) {
			return extractive(text);
		}
		String source = "IA: " + reply.providerName() + " · " + reply.modelName() + (partial ? " (parte inicial)" : "");
		return new Summary(answer, source);
	}

	/** Seleciona as frases com maior densidade de termos frequentes, preservando a ordem original. */
	static Summary extractive(String text) {
		List<String> sentences = splitSentences(text);
		Map<String, Integer> frequency = new HashMap<>();
		for (String sentence : sentences) {
			for (String word : words(sentence)) {
				frequency.merge(word, 1, Integer::sum);
			}
		}

		List<Integer> chosen = IntStream.range(0, sentences.size()).boxed()
				.sorted(Comparator.comparingDouble((Integer i) -> -score(sentences.get(i), frequency)))
				.limit(EXTRACTIVE_SENTENCES)
				.sorted()
				.toList();

		String keyTerms = frequency.entrySet().stream()
				.filter(e -> e.getValue() > 1)
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.limit(KEY_TERMS)
				.map(Map.Entry::getKey)
				.collect(Collectors.joining(", "));

		StringBuilder out = new StringBuilder("Pontos-chave:\n");
		for (int index : chosen) {
			out.append("• ").append(sentences.get(index)).append('\n');
		}
		if (!keyTerms.isBlank()) {
			out.append("Termos importantes: ").append(keyTerms);
		}
		return new Summary(out.toString().strip(), "Extrativo (sem IA)");
	}

	private static double score(String sentence, Map<String, Integer> frequency) {
		List<String> words = words(sentence);
		if (words.size() < 4) {
			return 0;
		}
		double total = words.stream().mapToInt(w -> frequency.getOrDefault(w, 0)).sum();
		// Normaliza pelo tamanho para não favorecer só frases longas.
		return total / Math.sqrt(words.size());
	}

	static List<String> splitSentences(String text) {
		List<String> sentences = new ArrayList<>();
		for (String block : text.split("\\n+")) {
			for (String sentence : block.split("(?<=[.!?])\\s+")) {
				String trimmed = sentence.strip().replaceFirst("^[•\\-*]\\s*", "");
				if (trimmed.length() >= 25 && trimmed.length() <= 400) {
					sentences.add(trimmed);
				}
			}
		}
		if (sentences.isEmpty()) {
			String fallback = text.strip();
			sentences.add(fallback.length() > 400 ? fallback.substring(0, 400) + "…" : fallback);
		}
		return sentences;
	}

	private static List<String> words(String sentence) {
		String normalized = Normalizer.normalize(sentence.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		List<String> result = new ArrayList<>();
		for (String token : normalized.split("[^a-z0-9]+")) {
			if (token.length() >= 4 && !STOPWORDS.contains(token)) {
				result.add(token);
			}
		}
		return result;
	}

	public record Summary(String text, String source) {}
}
