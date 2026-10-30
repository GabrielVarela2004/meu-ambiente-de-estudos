package br.com.estudos.estudos_backend.knowledge;

/** Decisão do usuário sobre um tópico sugerido (status {@link KnowledgeTopicStatus#PENDING_REVIEW}). */
public enum TopicReviewState {
	/** Ainda sem decisão nesta rodada. */
	PENDING,
	/** Relevante e bem definido: vira oficial ao publicar. */
	APPROVED,
	/** Irrelevante ou não técnico: arquivado ao publicar e lembrado nas próximas extrações. */
	REJECTED,
	/** Precisa de ajuste: a revisão com IA usa o comentário e devolve o tópico numa nova rodada. */
	FEEDBACK
}
