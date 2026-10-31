package br.com.estudos.estudos_backend;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * As tabelas do antigo Mapeamento de Fontes continuam no banco (sem uso) e têm chaves estrangeiras para blocos e
 * questões: os testes que apagam essas tabelas limpam antes as linhas que sobraram de execuções antigas.
 */
public final class LegacyTopicTables {

	private LegacyTopicTables() {
	}

	public static void clear(JdbcTemplate jdbcTemplate) {
		jdbcTemplate.update("DELETE FROM knowledge_topic_questions");
		jdbcTemplate.update("DELETE FROM topic_review_decisions");
		// Questões, tentativas e exercícios antigos ainda podem apontar para um tópico (coluna topic_id).
		for (String table : jdbcTemplate.queryForList("""
				SELECT table_name FROM information_schema.columns
				WHERE table_schema = current_schema() AND column_name = 'topic_id'
				  AND table_name <> 'knowledge_topic_questions'""", String.class)) {
			jdbcTemplate.update("UPDATE " + table + " SET topic_id = NULL WHERE topic_id IS NOT NULL");
		}
		jdbcTemplate.update("DELETE FROM knowledge_topics");
	}
}
