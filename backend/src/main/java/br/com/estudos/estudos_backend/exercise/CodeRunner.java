package br.com.estudos.estudos_backend.exercise;

import java.util.List;

/**
 * Porta para executar e analisar o código de um exercício prático num ambiente isolado.
 * O código nunca roda dentro do backend.
 */
public interface CodeRunner {

	/** Se o ambiente de execução está pronto (por exemplo, Docker rodando e imagem construída). */
	boolean isAvailable();

	/** Formato antigo: um único texto com um ou mais tipos (cada tipo de topo vira um arquivo no pacote padrão). */
	CodeEvaluationReport run(String submission, String testCode, List<String> conceptChecks);

	/** Projeto com vários arquivos (pacotes respeitados), corrigido com os testes do exercício. */
	CodeEvaluationReport runProject(List<ExerciseFile> files, String testCode, List<String> conceptChecks);

	/** Compila e executa o main do projeto com a entrada informada, sem testes. */
	CodeExecutionResult execute(List<ExerciseFile> files, String stdin);
}
