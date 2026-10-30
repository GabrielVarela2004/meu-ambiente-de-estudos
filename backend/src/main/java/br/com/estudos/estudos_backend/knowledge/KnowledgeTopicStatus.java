package br.com.estudos.estudos_backend.knowledge;

public enum KnowledgeTopicStatus {
	ACTIVE,
	ARCHIVED,
	/** Sugerido pela extração e aguardando validação na aba Mapeamento de Fontes: fora de questões e desempenho. */
	PENDING_REVIEW
}
