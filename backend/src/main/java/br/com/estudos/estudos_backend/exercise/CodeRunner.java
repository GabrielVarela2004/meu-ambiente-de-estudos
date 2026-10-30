package br.com.estudos.estudos_backend.exercise;

import java.util.List;

/**
 * Porta para executar e analisar o código de um exercício prático num ambiente isolado.
 * O código nunca roda dentro do backend.
 */
public interface CodeRunner {

	/** Se o ambiente de execução está pronto (por exemplo, Docker rodando e imagem construída). */
	boolean isAvailable();

	CodeEvaluationReport run(String submission, String testCode, List<String> conceptChecks);
}
